package com.gabarit.core.exam;

import com.gabarit.core.shared.NotFoundException;
import com.gabarit.core.shared.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ExamController.class)
@Import(SecurityConfig.class)
class ExamControllerTest {

	static final UUID ID = UUID.fromString("0193a1f0-0000-7000-8000-000000000007");

	static final UUID MISSING = UUID.fromString("0193a1f0-0000-7000-8000-000000000099");

	static final ExamSummary EXAM = new ExamSummary(ID, "Prefeitura de Santos - SP", "SP", null, null, "10 vagas", null,
			LocalDate.of(2026, 11, 20), null, ExamStatus.OPEN, "https://example.com/7", List.of());

	@Autowired
	MockMvc mvc;

	@MockitoBean
	ExamService service;

	@Test
	void semTokenE401() throws Exception {
		mvc.perform(get("/exams")).andExpect(status().isUnauthorized());
	}

	@Test
	void listaPaginadaComOsFiltros() throws Exception {
		Page<ExamSummary> page = new PageImpl<>(List.of(EXAM), PageRequest.of(1, 10), 11);
		given(service.search(any(), eq(1), eq(10))).willReturn(page);

		mvc.perform(get("/exams").with(jwt())
			.param("uf", "sp", "MG")
			.param("status", "OPEN")
			.param("q", " santos ")
			.param("page", "1")
			.param("size", "10"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].id").value(ID.toString()))
			.andExpect(jsonPath("$.items[0].status").value("OPEN"))
			.andExpect(jsonPath("$.items[0].registrationEnd").value("2026-11-20"))
			.andExpect(jsonPath("$.page").value(1))
			.andExpect(jsonPath("$.totalElements").value(11))
			.andExpect(jsonPath("$.totalPages").value(2));

		verify(service).search(new ExamFilter(Set.of("SP", "MG"), Set.of(ExamStatus.OPEN), null, "santos", null), 1, 10);
	}

	@Test
	void parametrosForaDoLimiteSao400() throws Exception {
		mvc.perform(get("/exams").with(jwt()).param("size", "51")).andExpect(status().isBadRequest());
		mvc.perform(get("/exams").with(jwt()).param("page", "-1")).andExpect(status().isBadRequest());
		mvc.perform(get("/exams").with(jwt()).param("status", "ABERTO"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.status").value(400));

		verifyNoInteractions(service);
	}

	@Test
	void concursoQueNaoExisteE404EmProblemDetail() throws Exception {
		given(service.get(MISSING)).willThrow(new NotFoundException("concurso não encontrado"));

		mvc.perform(get("/exams/" + MISSING).with(jwt()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.detail").value("concurso não encontrado"));
	}

	@Test
	void detalheComOsCargos() throws Exception {
		given(service.get(ID)).willReturn(new ExamDetail(ID, "Prefeitura de Santos - SP", "SP", null, null, null, null, null,
				null, ExamStatus.UNKNOWN, "https://example.com/7", List.of(new ExamDetail.Position(UUID.randomUUID(), "Enfermeiro"))));

		mvc.perform(get("/exams/" + ID).with(jwt()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.positions[0].name").value("Enfermeiro"));
	}

	@Test
	void tamanhoPadraoDaPagina() throws Exception {
		given(service.search(any(), anyInt(), anyInt())).willReturn(Page.empty());

		mvc.perform(get("/exams").with(jwt())).andExpect(status().isOk());

		verify(service).search(ExamFilter.all(), 0, 20);
	}

	@Test
	void idQueNaoEUuidE400() throws Exception {
		mvc.perform(get("/exams/7").with(jwt())).andExpect(status().isBadRequest());

		verifyNoInteractions(service);
	}

}
