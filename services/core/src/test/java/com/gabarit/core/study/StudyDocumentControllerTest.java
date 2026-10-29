package com.gabarit.core.study;

import com.gabarit.core.shared.SecurityConfig;
import com.gabarit.core.study.StudyDocumentService.DocumentView;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StudyDocumentController.class)
@Import(SecurityConfig.class)
class StudyDocumentControllerTest {

	static final UUID TARGET = UUID.fromString("0193a1f0-0000-7000-8000-000000000001");

	static final UUID DOCUMENT = UUID.fromString("0193a1f0-0000-7000-8000-000000000009");

	static final UUID PAPER = UUID.fromString("0193a1f0-0000-7000-8000-000000000003");

	static final DocumentView UPLOADING = new DocumentView(DOCUMENT, StudyDocument.Kind.EDITAL, "edital.pdf",
			StudyDocument.Status.UPLOADING, null, null, null, Instant.parse("2026-11-10T12:00:00Z"), null, null,
			null, null);

	@Autowired
	MockMvc mvc;

	@MockitoBean
	StudyDocumentService service;

	@Test
	void semTokenE401() throws Exception {
		mvc.perform(get("/me/study-targets/" + TARGET + "/materials")).andExpect(status().isUnauthorized());
	}

	@Test
	void comecarDevolveAUrlDeEnvio() throws Exception {
		given(service.start("kc-maria", TARGET, StudyDocument.Kind.EDITAL, null, "edital.pdf", 2048L))
			.willReturn(new StudyDocumentService.UploadStarted(UPLOADING, URI.create("http://minio.test/up"),
					"application/pdf"));

		mvc.perform(post("/me/study-targets/" + TARGET + "/documents").with(jwt().jwt(t -> t.subject("kc-maria")))
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"kind": "EDITAL", "name": "edital.pdf", "size": 2048}"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.uploadUrl").value("http://minio.test/up"))
			.andExpect(jsonPath("$.contentType").value("application/pdf"))
			.andExpect(jsonPath("$.document.status").value("UPLOADING"));
	}

	@Test
	void pedidoInvalidoE400() throws Exception {
		for (String body : new String[] { """
				{"kind": "FOTO", "name": "x.pdf", "size": 10}""", """
				{"kind": "EDITAL", "name": "", "size": 10}""", """
				{"kind": "EDITAL", "name": "x.pdf"}""", """
				{"kind": "EDITAL", "name": "x.pdf", "size": 0}""" }) {
			mvc.perform(post("/me/study-targets/" + TARGET + "/documents").with(jwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content(body)).andExpect(status().isBadRequest());
		}
		verifyNoInteractions(service);
	}

	@Test
	void novaProvaAnterior() throws Exception {
		given(service.addPaper("kc-maria", TARGET, 2019, "Tipo 1", true))
			.willReturn(new StudyDocumentService.PaperView(PAPER, 2019, "Tipo 1", true, Instant.parse("2026-11-10T12:00:00Z"),
					null, null, null, null));

		mvc.perform(post("/me/study-targets/" + TARGET + "/papers").with(jwt().jwt(t -> t.subject("kc-maria")))
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"year": 2019, "variant": "Tipo 1", "answersIncluded": true}"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.id").value(PAPER.toString()))
			.andExpect(jsonPath("$.answersIncluded").value(true));
	}

	@Test
	void anoForaDoLimiteE400() throws Exception {
		mvc.perform(post("/me/study-targets/" + TARGET + "/papers").with(jwt())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"year": 1800}"""))
			.andExpect(status().isBadRequest());
	}

	@Test
	void confirmar() throws Exception {
		given(service.complete("kc-maria", TARGET, DOCUMENT)).willReturn(UPLOADING);

		mvc.perform(post("/me/study-targets/" + TARGET + "/documents/" + DOCUMENT + "/complete").with(jwt().jwt(t -> t.subject("kc-maria"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(DOCUMENT.toString()));
	}

}
