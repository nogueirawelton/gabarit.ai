package com.gabarit.core.user;

import com.gabarit.core.shared.BadRequestException;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Set;

/**
 * Foto de perfil normalizada: quadrada (recorte central), no máximo {@value #SIZE}px, JPEG sem
 * metadados (EXIF com localização etc. some no re-encode). O web já manda recortada e reduzida;
 * aqui é a garantia para quem chama a API direto, nunca confiamos no arquivo recebido.
 */
final class AvatarImage {

	static final int SIZE = 512;

	static final String CONTENT_TYPE = "image/jpeg";

	private static final Set<String> FORMATS = Set.of("jpeg", "png");

	// lado de 8000px já é foto de câmera profissional: acima disso só pode ser imagem forjada para
	// estourar a memória ao decodificar (o arquivo comprimido é pequeno, o bitmap não)
	private static final long MAX_PIXELS = 8000L * 8000L;

	private static final float JPEG_QUALITY = 0.85f;

	static {
		// decodifica tudo em memória (o arquivo já está limitado pelo multipart), sem arquivo temporário
		ImageIO.setUseCache(false);
	}

	private AvatarImage() {
	}

	/** @throws BadRequestException não é JPEG/PNG, está corrompida ou é grande demais */
	static byte[] normalize(byte[] content) {
		try {
			return encode(square(read(content)));
		}
		catch (IOException e) {
			throw new BadRequestException("imagem inválida ou corrompida");
		}
	}

	private static BufferedImage read(byte[] content) throws IOException {
		try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
			// o formato vem dos bytes do arquivo, não do nome nem do Content-Type
			Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
			if (!readers.hasNext()) {
				throw new BadRequestException("envie uma imagem JPEG ou PNG");
			}
			ImageReader reader = readers.next();
			try {
				if (!FORMATS.contains(reader.getFormatName().toLowerCase())) {
					throw new BadRequestException("envie uma imagem JPEG ou PNG");
				}
				reader.setInput(in, true, true);
				// só o cabeçalho: confere o tamanho antes de alocar o bitmap
				if ((long) reader.getWidth(0) * reader.getHeight(0) > MAX_PIXELS) {
					throw new BadRequestException("imagem grande demais");
				}
				return reader.read(0);
			}
			finally {
				reader.dispose();
			}
		}
	}

	/** Recorte central quadrado, reduzido pela metade em passos até chegar perto do tamanho final. */
	private static BufferedImage square(BufferedImage source) {
		int side = Math.min(source.getWidth(), source.getHeight());
		BufferedImage image = source.getSubimage((source.getWidth() - side) / 2, (source.getHeight() - side) / 2,
				side, side);
		int target = Math.min(side, SIZE);
		// reduzir de uma vez (ex.: 4000 → 512) serrilha; pela metade a cada passo fica suave
		while (side / 2 >= target) {
			side /= 2;
			image = draw(image, side);
		}
		// sempre redesenha no fim: tira a transparência (fundo branco) e deixa RGB para o JPEG
		return draw(image, target);
	}

	private static BufferedImage draw(BufferedImage source, int side) {
		BufferedImage out = new BufferedImage(side, side, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = out.createGraphics();
		try {
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			g.setColor(Color.WHITE);
			g.fillRect(0, 0, side, side);
			g.drawImage(source, 0, 0, side, side, null);
		}
		finally {
			g.dispose();
		}
		return out;
	}

	private static byte[] encode(BufferedImage image) throws IOException {
		ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
		ImageWriteParam param = writer.getDefaultWriteParam();
		param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
		param.setCompressionQuality(JPEG_QUALITY);
		var bytes = new ByteArrayOutputStream();
		try (ImageOutputStream out = ImageIO.createImageOutputStream(bytes)) {
			writer.setOutput(out);
			writer.write(null, new IIOImage(image, null, null), param);
		}
		finally {
			writer.dispose();
		}
		return bytes.toByteArray();
	}

}
