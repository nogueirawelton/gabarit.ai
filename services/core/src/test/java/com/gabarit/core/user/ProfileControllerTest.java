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

	@Test
	void semTokenE401() throws Exception {
		mvc.perform(get("/me/profile")).andExpect(status().isUnauthorized());
	}

	@Test
	void usuarioVemDoTokenNuncaDoCorpo() throws Exception {
		given(service.update(eq("kc-maria"), anyCollection(), anyCollection()))
			.willReturn(new UserProfileService.ProfileView(List.of(), List.of("SP")));

		mvc.perform(put("/me/profile").with(jwt().jwt(t -> t.subject("kc-maria")))
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"positionIds": [], "ufs": ["SP"], "userId": "kc-outra-pessoa"}"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.ufs[0]").value("SP"));

		verify(service).update(eq("kc-maria"), anyCollection(), anyCollection());
	}

	@Test
	void corpoInvalidoE400NoFormatoProblemDetail() throws Exception {
		mvc.perform(put("/me/profile").with(jwt())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"positionIds": null, "ufs": ["SP"]}"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.status").value(400));
	}

	@Test
	void erroDeRegraVira400ComODetalhe() throws Exception {
		given(service.update(eq("kc-maria"), anyCollection(), anyCollection()))
			.willThrow(new BadRequestException("UF inválida: XX"));

		mvc.perform(put("/me/profile").with(jwt().jwt(t -> t.subject("kc-maria")))
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"positionIds": [], "ufs": ["XX"]}"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("UF inválida: XX"));
	}

}
