package com.gabarit.core.study;

import com.gabarit.core.shared.SecurityConfig;
import com.gabarit.core.study.StudyTargetService.StudyTargetDetail;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StudyTargetController.class)
@Import(SecurityConfig.class)
class StudyTargetControllerTest {

	static final UUID ID = UUID.fromString("0193a1f0-0000-7000-8000-000000000005");

	static final UUID EXAM_ID = UUID.fromString("0193a1f0-0000-7000-8000-000000000007");

	static final StudyTargetDetail TARGET = new StudyTargetDetail(ID, EXAM_ID, "Prefeitura de Santos - SP",
			"Prefeitura de Santos", null, 2026, null, null, Instant.parse("2026-11-10T12:00:00Z"), null, List.of());

	@Autowired
	MockMvc mvc;

	@MockitoBean
	StudyTargetService service;

	@Test
	void semTokenE401() throws Exception {
		mvc.perform(get("/me/study-targets")).andExpect(status().isUnauthorized());
	}

	@Test
	void adicionarConcursoDaListaE201ComLocation() throws Exception {
		given(service.addExam("kc-maria", EXAM_ID)).willReturn(new StudyTargetService.Added(TARGET, true));

		mvc.perform(post("/me/study-targets").with(jwt().jwt(t -> t.subject("kc-maria")))
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"examId": "0193a1f0-0000-7000-8000-000000000007"}"""))
			.andExpect(status().isCreated())
			.andExpect(header().string("Location", "/me/study-targets/" + ID))
			.andExpect(jsonPath("$.organ").value("Prefeitura de Santos"));
	}

	@Test
	void concursoQueJaEstavaE200() throws Exception {
		given(service.addExam("kc-maria", EXAM_ID)).willReturn(new StudyTargetService.Added(TARGET, false));

		mvc.perform(post("/me/study-targets").with(jwt().jwt(t -> t.subject("kc-maria")))
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"examId": "0193a1f0-0000-7000-8000-000000000007"}"""))
			.andExpect(status().isOk());
	}

	@Test
	void concursoAMaoPeloTitulo() throws Exception {
		given(service.addCustom("kc-maria", "TJ-SP 2023")).willReturn(TARGET);

		mvc.perform(post("/me/study-targets").with(jwt().jwt(t -> t.subject("kc-maria")))
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"title": "TJ-SP 2023"}"""))
			.andExpect(status().isCreated());

		verify(service).addCustom("kc-maria", "TJ-SP 2023");
	}

	@Test
	void precisaDeExatamenteUmDosDois() throws Exception {
		for (String body : new String[] { "{}", """
				{"examId": "0193a1f0-0000-7000-8000-000000000007", "title": "x"}""", """
				{"title": "   "}""" }) {
			mvc.perform(post("/me/study-targets").with(jwt()).contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest());
		}
		verifyNoInteractions(service);
	}

	@Test
	void edicaoValidaOsCampos() throws Exception {
		mvc.perform(put("/me/study-targets/" + ID).with(jwt())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"title": "", "year": 1800}"""))
			.andExpect(status().isBadRequest());

		verifyNoInteractions(service);
	}

	@Test
	void edicao() throws Exception {
		given(service.update(anyString(), any(), any())).willReturn(TARGET);

		mvc.perform(put("/me/study-targets/" + ID).with(jwt().jwt(t -> t.subject("kc-maria")))
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"title": "Santos", "organ": "Prefeitura", "year": 2026, "examDate": "2027-01-20"}"""))
			.andExpect(status().isOk());

		verify(service).update("kc-maria", ID, new StudyTargetService.Details("Santos", "Prefeitura", null, 2026, null,
				java.time.LocalDate.of(2027, 1, 20)));
	}

	@Test
	void remover() throws Exception {
		mvc.perform(delete("/me/study-targets/" + ID).with(jwt().jwt(t -> t.subject("kc-maria"))))
			.andExpect(status().isNoContent());

		verify(service).remove("kc-maria", ID);
	}

}
