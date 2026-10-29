package com.gabarit.core.user;

import com.gabarit.core.shared.BadRequestException;
import com.gabarit.core.shared.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProfileController.class)
@Import(SecurityConfig.class)
class ProfileControllerTest {

	@Autowired
	MockMvc mvc;

	@MockitoBean
	UserProfileService service;

	@MockitoBean
	AvatarService avatars;

	@Test
	void semTokenE401() throws Exception {
		mvc.perform(get("/me/profile")).andExpect(status().isUnauthorized());
	}

	@Test
	void usuarioVemDoTokenNuncaDoCorpo() throws Exception {
		given(service.update(eq("kc-maria"), anyCollection(), anyCollection(), anyCollection()))
			.willReturn(new UserProfileService.ProfileView(List.of(), List.of("SP"), List.of(), null, true));

		mvc.perform(put("/me/profile").with(jwt().jwt(t -> t.subject("kc-maria")))
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"positionIds": [], "ufs": ["SP"], "educationLevels": [], "userId": "kc-outra-pessoa"}"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.ufs[0]").value("SP"));

		verify(service).update(eq("kc-maria"), anyCollection(), anyCollection(), anyCollection());
	}

	@Test
	void devolveAUrlDaFotoQuandoHa() throws Exception {
		given(service.get("kc-maria"))
			.willReturn(new UserProfileService.ProfileView(List.of(), List.of(), List.of(), "kc-maria/foto.jpg", true));
		given(avatars.url("kc-maria/foto.jpg")).willReturn(URI.create("http://minio.test/avatars/kc-maria/foto.jpg"));

		mvc.perform(get("/me/profile").with(jwt().jwt(t -> t.subject("kc-maria"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.avatarUrl").value("http://minio.test/avatars/kc-maria/foto.jpg"));
	}

	@Test
	void corpoInvalidoE400NoFormatoProblemDetail() throws Exception {
		mvc.perform(put("/me/profile").with(jwt())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"positionIds": null, "ufs": ["SP"], "educationLevels": []}"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.status").value(400));
	}

	@Test
	void guardaOContatoDoTokenAoAbrirOPerfil() throws Exception {
		given(service.get("kc-maria")).willReturn(UserProfileService.ProfileView.EMPTY);

		mvc.perform(get("/me/profile").with(jwt().jwt(t -> t.subject("kc-maria")
			.claim("email", "maria@exemplo.com")
			.claim("email_verified", true)
			.claim("given_name", "Maria"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.emailDigest").value(true));

		verify(service).rememberContact("kc-maria", "maria@exemplo.com", true, "Maria");
	}

	@Test
	void desligaOResumoPorEmail() throws Exception {
		given(service.changeEmailDigest("kc-maria", false))
			.willReturn(new UserProfileService.ProfileView(List.of(), List.of(), List.of(), null, false));

		mvc.perform(put("/me/profile/email-digest").with(jwt().jwt(t -> t.subject("kc-maria")))
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"emailDigest": false}"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.emailDigest").value(false));
	}

	@Test
	void escolaridadeDesconhecidaE400() throws Exception {
		mvc.perform(put("/me/profile").with(jwt())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"positionIds": [], "ufs": [], "educationLevels": ["DOUTORADO"]}"""))
			.andExpect(status().isBadRequest());
	}

	@Test
	void erroDeRegraVira400ComODetalhe() throws Exception {
		given(service.update(eq("kc-maria"), anyCollection(), anyCollection(), anyCollection()))
			.willThrow(new BadRequestException("UF inválida: XX"));

		mvc.perform(put("/me/profile").with(jwt().jwt(t -> t.subject("kc-maria")))
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"positionIds": [], "ufs": ["XX"], "educationLevels": []}"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("UF inválida: XX"));
	}

}
