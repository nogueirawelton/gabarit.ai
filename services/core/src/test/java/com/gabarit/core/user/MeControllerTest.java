package com.gabarit.core.user;

import com.gabarit.core.shared.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MeController.class)
@Import(SecurityConfig.class)
class MeControllerTest {

	@Autowired
	MockMvc mvc;

	@Test
	void semTokenE401() throws Exception {
		mvc.perform(get("/me")).andExpect(status().isUnauthorized());
	}

	@Test
	void comTokenDevolveOUsuarioDoToken() throws Exception {
		mvc.perform(get("/me").with(jwt().jwt(t -> t.subject("kc-123")
			.claim("email", "maria@exemplo.com")
			.claim("name", "Maria Teste")
			.claim("given_name", "Maria"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value("kc-123"))
			.andExpect(jsonPath("$.email").value("maria@exemplo.com"))
			.andExpect(jsonPath("$.givenName").value("Maria"));
	}

	@Test
	void corsLiberaSoAOrigemDoWeb() throws Exception {
		mvc.perform(options("/me").header("Origin", "http://localhost:5173")
			.header("Access-Control-Request-Method", "GET"))
			.andExpect(status().isOk())
			.andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));

		mvc.perform(options("/me").header("Origin", "https://outro-site.com")
			.header("Access-Control-Request-Method", "GET"))
			.andExpect(status().isForbidden());
	}

}
