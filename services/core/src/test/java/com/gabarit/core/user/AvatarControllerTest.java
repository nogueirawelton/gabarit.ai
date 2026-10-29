package com.gabarit.core.user;

import com.gabarit.core.shared.BadRequestException;
import com.gabarit.core.shared.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AvatarController.class)
@Import(SecurityConfig.class)
class AvatarControllerTest {

	static final MockMultipartFile FILE = new MockMultipartFile("file", "foto.jpg", "image/jpeg", new byte[] { 1, 2, 3 });

	@Autowired
	MockMvc mvc;

	@MockitoBean
	AvatarService avatars;

	@Test
	void semTokenE401() throws Exception {
		mvc.perform(multipart("/me/avatar").file(FILE)).andExpect(status().isUnauthorized());
	}

	@Test
	void uploadEDoUsuarioDoToken() throws Exception {
		given(avatars.upload(eq("kc-maria"), any())).willReturn(URI.create("http://minio.test/avatars/x.jpg"));

		mvc.perform(multipart("/me/avatar").file(FILE).with(jwt().jwt(t -> t.subject("kc-maria"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.url").value("http://minio.test/avatars/x.jpg"));

		verify(avatars).upload("kc-maria", new byte[] { 1, 2, 3 });
	}

	@Test
	void semArquivoE400() throws Exception {
		mvc.perform(multipart("/me/avatar").with(jwt())).andExpect(status().isBadRequest());
	}

	@Test
	void imagemInvalidaE400ComODetalhe() throws Exception {
		given(avatars.upload(eq("kc-maria"), any())).willThrow(new BadRequestException("envie uma imagem JPEG ou PNG"));

		mvc.perform(multipart("/me/avatar").file(FILE).with(jwt().jwt(t -> t.subject("kc-maria"))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("envie uma imagem JPEG ou PNG"));
	}

	@Test
	void removerE204() throws Exception {
		mvc.perform(delete("/me/avatar").with(jwt().jwt(t -> t.subject("kc-maria")))).andExpect(status().isNoContent());

		verify(avatars).remove("kc-maria");
	}

}
