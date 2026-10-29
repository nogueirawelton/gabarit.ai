package com.gabarit.core.document;

import com.gabarit.core.MinioTestConfiguration;
import com.gabarit.core.PostgresTestConfiguration;
import com.gabarit.core.shared.BadRequestException;
import com.gabarit.core.shared.ClockConfig;
import com.gabarit.core.shared.ObjectStorage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

import static com.gabarit.core.document.TestPdfs.get;
import static com.gabarit.core.document.TestPdfs.pdf;
import static com.gabarit.core.document.TestPdfs.put;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** MinIO e Postgres reais; o envio é feito como o navegador faz (PUT na URL pré-assinada). */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ PostgresTestConfiguration.class, MinioTestConfiguration.class, DocumentService.class, ObjectStorage.class,
		ClockConfig.class })
class DocumentServiceTest {

	@Autowired
	DocumentService service;

	@Autowired
	DocumentBlobRepository blobs;

	@Autowired
	ObjectStorage storage;

	static String newId() {
		return "teste/" + UUID.randomUUID();
	}

	String upload(byte[] content) {
		String id = newId();
		assertThat(put(service.uploadUrl(id).url(), "application/pdf", content)).isEqualTo(200);
		return id;
	}

	@Test
	void pdfValidoViraBlobComHashEPaginas() throws Exception {
		byte[] content = pdf(3, "valido");
		String id = upload(content);

		var blob = service.ingest(id);

		assertThat(blob.pages()).isEqualTo(3);
		assertThat(blob.sizeBytes()).isEqualTo(content.length);
		assertThat(blob.sha256())
			.isEqualTo(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)));
		assertThat(storage.size("documents", "uploads/" + id + ".pdf")).as("envio apagado").isEmpty();
		var view = get(service.viewUrl(blob.sha256()));
		assertThat(view.statusCode()).isEqualTo(200);
		assertThat(view.headers().firstValue("Content-Type")).hasValue("application/pdf");
		assertThat(view.body()).isEqualTo(content);
	}

	@Test
	void mesmoArquivoDuasVezesEUmBlobSo() {
		byte[] content = pdf(1, "repetido");

		var first = service.ingest(upload(content));
		var second = service.ingest(upload(content));

		assertThat(second.sha256()).isEqualTo(first.sha256());
		assertThat(blobs.count()).isEqualTo(1);
	}

	@Test
	void naoEPdf() {
		String id = upload("<html>não sou PDF</html>".getBytes(StandardCharsets.UTF_8));

		assertThatThrownBy(() -> service.ingest(id)).isInstanceOf(BadRequestException.class)
			.hasMessageContaining("não é um PDF");
		assertThat(storage.size("documents", "uploads/" + id + ".pdf")).as("recusado não fica").isEmpty();
	}

	@Test
	void pdfCorrompido() {
		String id = upload("%PDF-1.7\nlixo que não é PDF de verdade".getBytes(StandardCharsets.UTF_8));

		assertThatThrownBy(() -> service.ingest(id)).isInstanceOf(BadRequestException.class)
			.hasMessageContaining("corrompido");
	}

	@Test
	void pdfComSenha() {
		String id = upload(TestPdfs.withPassword());

		assertThatThrownBy(() -> service.ingest(id)).isInstanceOf(BadRequestException.class)
			.hasMessageContaining("senha");
	}

	@Test
	void envioQueNaoChegou() {
		assertThatThrownBy(() -> service.ingest(newId())).isInstanceOf(BadRequestException.class)
			.hasMessageContaining("não chegou");
	}

	@Test
	void urlDeEnvioSoAceitaPdf() {
		// o Content-Type entra na assinatura: mudar no envio invalida a URL
		assertThat(put(service.uploadUrl(newId()).url(), "text/html", pdf(1, "x"))).isEqualTo(403);
	}

}
