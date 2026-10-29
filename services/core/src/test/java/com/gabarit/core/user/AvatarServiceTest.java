package com.gabarit.core.user;

import com.gabarit.core.MinioTestConfiguration;
import com.gabarit.core.PostgresTestConfiguration;
import com.gabarit.core.shared.BadRequestException;
import com.gabarit.core.shared.ObjectStorage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Banco e MinIO reais (com o usuário restrito do core); a foto é lida pela URL, como o navegador faz. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ PostgresTestConfiguration.class, MinioTestConfiguration.class, AvatarService.class, ObjectStorage.class })
class AvatarServiceTest {

	static final HttpClient HTTP = HttpClient.newHttpClient();

	@Autowired
	AvatarService service;

	@Autowired
	UserProfileRepository repository;

	@Test
	void uploadGuardaAFotoEDevolveUmaUrlQueONavegadorLe() throws Exception {
		URI url = service.upload("kc-1", png());

		HttpResponse<byte[]> response = get(url);
		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.headers().firstValue("Content-Type")).hasValue("image/jpeg");
		assertThat(repository.findById("kc-1")).get()
			.extracting(UserProfile::getAvatarKey)
			.asString()
			.startsWith("kc-1/");
	}

	@Test
	void trocarAFotoApagaAAnterior() throws Exception {
		URI first = service.upload("kc-2", png());

		URI second = service.upload("kc-2", png());

		assertThat(second).isNotEqualTo(first);
		assertThat(get(first).statusCode()).isEqualTo(404);
		assertThat(get(second).statusCode()).isEqualTo(200);
	}

	@Test
	void removerApagaAFoto() throws Exception {
		URI url = service.upload("kc-3", png());

		service.remove("kc-3");

		assertThat(repository.findById("kc-3")).get().extracting(UserProfile::getAvatarKey).isNull();
		assertThat(get(url).statusCode()).isEqualTo(404);
	}

	@Test
	void removerSemFotoNaoFazNada() {
		service.remove("kc-sem-perfil");

		assertThat(repository.findById("kc-sem-perfil")).isEmpty();
	}

	@Test
	void arquivoInvalidoNaoMexeNoPerfil() {
		assertThatThrownBy(() -> service.upload("kc-4", "não é imagem".getBytes()))
			.isInstanceOf(BadRequestException.class);

		assertThat(repository.findById("kc-4")).isEmpty();
	}

	@Test
	void mesmaFotoDevolveAMesmaUrl() throws Exception {
		service.upload("kc-5", png());
		String key = repository.findById("kc-5").orElseThrow().getAvatarKey();

		// URL estável: o <img> não baixa a foto de novo a cada leitura do perfil
		assertThat(service.url(key)).isEqualTo(service.url(key));
		assertThat(service.url(null)).isNull();
	}

	static byte[] png() throws IOException {
		return AvatarImageTest.image("png", 800, 600, BufferedImage.TYPE_INT_RGB);
	}

	static HttpResponse<byte[]> get(URI url) throws IOException, InterruptedException {
		return HTTP.send(HttpRequest.newBuilder(url).build(), HttpResponse.BodyHandlers.ofByteArray());
	}

}
