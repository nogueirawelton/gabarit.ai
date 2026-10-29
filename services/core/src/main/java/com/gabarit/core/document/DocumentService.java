package com.gabarit.core.document;

import com.gabarit.core.shared.BadRequestException;
import com.gabarit.core.shared.ObjectStorage;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.HexFormat;

/**
 * PDFs enviados pelo navegador direto ao MinIO. O core entrega a URL de envio e, depois, confere o
 * arquivo (nunca confia no que o navegador mandou): é PDF, abre, tamanho e páginas no limite. O
 * arquivo aprovado vira um blob por conteúdo ({@code blobs/<sha256>.pdf}).
 *
 * <p>Não conhece carteira nem tipo de documento: quem dá sentido ao arquivo é quem chama.
 */
@Service
public class DocumentService {

	/** Limite de envio (a API do Claude aceita até 32 MB por requisição). */
	public static final long MAX_BYTES = 30L * 1024 * 1024;

	/** Limite de páginas da API do Claude por requisição. */
	public static final int MAX_PAGES = 600;

	static final String PDF = "application/pdf";

	private static final Duration UPLOAD_URL_TTL = Duration.ofMinutes(15);

	private static final Duration VIEW_URL_TTL = Duration.ofHours(1);

	private static final byte[] PDF_MAGIC = "%PDF-".getBytes();

	/** Arquivo conferido. */
	public record Blob(String sha256, long sizeBytes, int pages) {
	}

	/** Onde o navegador envia (PUT, com {@code Content-Type: application/pdf}) e até quando. */
	public record UploadUrl(URI url, String contentType) {
	}

	private final ObjectStorage storage;

	private final DocumentBlobRepository blobs;

	private final TransactionTemplate transaction;

	private final Clock clock;

	private final String bucket;

	DocumentService(ObjectStorage storage, DocumentBlobRepository blobs, TransactionTemplate transaction, Clock clock,
			@Value("${gabarit.storage.documents-bucket}") String bucket) {
		this.storage = storage;
		this.blobs = blobs;
		this.transaction = transaction;
		this.clock = clock;
		this.bucket = bucket;
	}

	/**
	 * URL para enviar um arquivo. {@code uploadId} é de quem chama (ex.: {@code <usuário>/<id>}), único
	 * e só com caracteres seguros para chave de objeto.
	 */
	public UploadUrl uploadUrl(String uploadId) {
		return new UploadUrl(storage.presignedPut(bucket, uploadKey(uploadId), PDF, UPLOAD_URL_TTL), PDF);
	}

	/**
	 * Confere o arquivo enviado e o guarda como blob. Arquivo reprovado é apagado; com erro de rede
	 * ou disco, o envio fica para tentar de novo.
	 *
	 * @throws BadRequestException não chegou, não é PDF, está corrompido ou protegido por senha, ou
	 * passa do limite de tamanho ou de páginas (a mensagem é para o usuário)
	 */
	public Blob ingest(String uploadId) {
		String key = uploadKey(uploadId);
		long size = storage.size(bucket, key)
			.orElseThrow(() -> new BadRequestException("o arquivo não chegou: envie de novo"));
		Path file = null;
		try {
			Blob blob = store(key, size, file = Files.createTempFile("upload-", ".pdf"));
			// aprovado: já copiado para blobs/
			storage.delete(bucket, key);
			return blob;
		}
		catch (BadRequestException e) {
			// recusado: não fica guardado
			storage.delete(bucket, key);
			throw e;
		}
		catch (IOException e) {
			// erro de rede/disco: o envio fica, e confirmar de novo tenta de novo
			throw new UncheckedIOException(e);
		}
		finally {
			if (file != null) {
				file.toFile().delete();
			}
		}
	}

	private Blob store(String key, long size, Path file) throws IOException {
		if (size > MAX_BYTES) {
			throw new BadRequestException("arquivo grande demais (máximo de %d MB)".formatted(MAX_BYTES / 1024 / 1024));
		}
		String sha256 = download(key, file);
		int pages = pages(file);
		if (pages > MAX_PAGES) {
			throw new BadRequestException("PDF com páginas demais (máximo de %d)".formatted(MAX_PAGES));
		}
		String blobKey = blobKey(sha256);
		if (!blobs.existsById(sha256)) {
			// cópia antes do registro: blob registrado sempre tem arquivo (copiar de novo é inofensivo)
			storage.copy(bucket, key, blobKey, PDF);
			transaction.executeWithoutResult(status -> blobs.insertIfAbsent(sha256, blobKey, size, pages, clock.instant()));
		}
		return new Blob(sha256, size, pages);
	}

	/** URL para o dono abrir o arquivo no navegador, até expirar. */
	public URI viewUrl(String sha256) {
		return storage.presignedGet(bucket, blobKey(sha256), VIEW_URL_TTL);
	}

	/** Chave do PDF no bucket de documentos (é o que o worker extraction lê). */
	public static String blobKey(String sha256) {
		return "blobs/%s.pdf".formatted(sha256);
	}

	private static String uploadKey(String uploadId) {
		return "uploads/%s.pdf".formatted(uploadId);
	}

	/** Baixa para o arquivo temporário calculando o SHA-256 no caminho; confere o cabeçalho de PDF. */
	private String download(String key, Path file) throws IOException {
		MessageDigest digest = sha256();
		try (InputStream in = new DigestInputStream(storage.read(bucket, key), digest)) {
			Files.copy(in, file, StandardCopyOption.REPLACE_EXISTING);
		}
		byte[] head = new byte[PDF_MAGIC.length];
		try (InputStream in = Files.newInputStream(file)) {
			if (in.readNBytes(head, 0, head.length) < head.length || !Arrays.equals(head, PDF_MAGIC)) {
				throw new BadRequestException("o arquivo não é um PDF");
			}
		}
		return HexFormat.of().formatHex(digest.digest());
	}

	/** Abre o PDF de verdade (cache em disco, não em memória) só para contar as páginas. */
	private static int pages(Path file) {
		try (PDDocument pdf = Loader.loadPDF(file.toFile(), IOUtils.createTempFileOnlyStreamCache())) {
			return pdf.getNumberOfPages();
		}
		catch (InvalidPasswordException e) {
			throw new BadRequestException("PDF protegido por senha: envie uma versão sem senha");
		}
		catch (IOException e) {
			throw new BadRequestException("PDF inválido ou corrompido");
		}
	}

	private static MessageDigest sha256() {
		try {
			return MessageDigest.getInstance("SHA-256");
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("JVM sem SHA-256", e);
		}
	}

}
