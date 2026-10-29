package com.gabarit.core.user;

import com.gabarit.core.shared.BadRequestException;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AvatarImageTest {

	@Test
	void fotoGrandeViraJpegQuadradoNoTamanhoMaximo() throws IOException {
		byte[] jpeg = AvatarImage.normalize(image("png", 1600, 900, BufferedImage.TYPE_INT_ARGB));

		BufferedImage result = ImageIO.read(new ByteArrayInputStream(jpeg));
		assertThat(result.getWidth()).isEqualTo(AvatarImage.SIZE);
		assertThat(result.getHeight()).isEqualTo(AvatarImage.SIZE);
		assertThat(ImageIO.getImageReaders(ImageIO.createImageInputStream(new ByteArrayInputStream(jpeg))).next()
			.getFormatName()).isEqualToIgnoringCase("jpeg");
	}

	@Test
	void fotoPequenaSoERecortadaSemAumentar() throws IOException {
		byte[] jpeg = AvatarImage.normalize(image("jpeg", 120, 200, BufferedImage.TYPE_INT_RGB));

		BufferedImage result = ImageIO.read(new ByteArrayInputStream(jpeg));
		assertThat(result.getWidth()).isEqualTo(120);
		assertThat(result.getHeight()).isEqualTo(120);
	}

	@Test
	void recusaArquivoQueNaoEImagem() {
		assertThatThrownBy(() -> AvatarImage.normalize("<svg/>".getBytes(StandardCharsets.UTF_8)))
			.isInstanceOf(BadRequestException.class);
	}

	@Test
	void recusaOutrosFormatos() throws IOException {
		assertThatThrownBy(() -> AvatarImage.normalize(image("gif", 50, 50, BufferedImage.TYPE_INT_RGB)))
			.isInstanceOf(BadRequestException.class)
			.hasMessageContaining("JPEG ou PNG");
	}

	@Test
	void recusaImagemCorrompida() throws IOException {
		byte[] png = image("png", 300, 300, BufferedImage.TYPE_INT_RGB);
		byte[] truncated = java.util.Arrays.copyOf(png, 60);

		assertThatThrownBy(() -> AvatarImage.normalize(truncated)).isInstanceOf(BadRequestException.class);
	}

	static byte[] image(String format, int width, int height, int type) throws IOException {
		BufferedImage image = new BufferedImage(width, height, type);
		var g = image.createGraphics();
		g.setColor(Color.ORANGE);
		g.fillRect(0, 0, width / 2, height);
		g.dispose();
		var out = new ByteArrayOutputStream();
		ImageIO.write(image, format, out);
		return out.toByteArray();
	}

}
