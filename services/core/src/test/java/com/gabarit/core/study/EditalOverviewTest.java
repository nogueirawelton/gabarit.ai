package com.gabarit.core.study;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** Lê a saída real do worker (edital do MPRJ 2025, FGV) e devolve ao web em camelCase. */
class EditalOverviewTest {

	static String result() throws IOException {
		try (InputStream in = EditalOverviewTest.class.getResourceAsStream("/extraction/edital-overview-mprj.json")) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	@Test
	void leOResultadoDoWorker() throws IOException {
		EditalOverview overview = EditalOverview.parse(result());

		assertThat(overview.organ()).isEqualTo("Ministério Público do Estado do Rio de Janeiro");
		assertThat(overview.board()).isEqualTo("FGV");
		assertThat(overview.year()).isEqualTo(2025);
		assertThat(overview.registrationStart()).isEqualTo(LocalDate.of(2025, 2, 5));
		assertThat(overview.examDate()).isNull();
		assertThat(overview.positions()).hasSize(5).first().satisfies(p -> {
			assertThat(p.name()).isEqualTo("Analista do Ministério Público");
			assertThat(p.specialty()).isEqualTo("Administrativa");
			assertThat(p.educationLevel()).isEqualTo("SUPERIOR");
			assertThat(p.openings()).isEqualTo("10");
		});
		// leitura v1: sem quadro de provas
		assertThat(overview.exams()).isEmpty();
	}

	@Test
	void v2TrazOQuadroDeProvasEAProvaDeCadaCargo() {
		EditalOverview overview = EditalOverview.parse("""
				{"organ": "INB", "board": "Consulplan", "year": 2026, "registration_start": null,
				 "registration_end": null, "exam_date": null,
				 "positions": [{"name": "Analista de Sistemas", "specialty": null, "education_level": "SUPERIOR",
				   "openings": "1", "salary": null, "page": 3, "exam_id": 1}],
				 "exams": [{"id": 1, "applies_to": "Cargos de nível superior", "answer_format": "MULTIPLE_CHOICE",
				   "total_questions": 60, "essay": true, "sections": [
				     {"block": "Conhecimentos Básicos", "subject": "Língua Portuguesa", "questions": 10, "weight": 1.0},
				     {"block": "Conhecimentos Específicos", "subject": "Conhecimentos Específicos", "questions": 40,
				      "weight": 2.5}]}]}
				""");

		assertThat(overview.positions().getFirst().examId()).isEqualTo(1);
		assertThat(overview.exams()).singleElement().satisfies(exam -> {
			assertThat(exam.appliesTo()).isEqualTo("Cargos de nível superior");
			assertThat(exam.totalQuestions()).isEqualTo(60);
			assertThat(exam.essay()).isTrue();
			assertThat(exam.sections()).extracting(EditalOverview.Section::questions).containsExactly(10, 40);
		});
	}

	@Test
	void vaiParaOWebEmCamelCase() throws IOException {
		String json = JsonMapper.builder().build().writeValueAsString(EditalOverview.parse(result()));

		assertThat(json).contains("\"registrationStart\":\"2025-02-05\"", "\"educationLevel\":\"SUPERIOR\"")
			.doesNotContain("registration_start");
	}

	@Test
	void mesmaDisciplinaComNomesUmPoucoDiferentes() {
		assertThat(EditalOverview.sameSubject("Conhecimento de Normas", "Conhecimentos de Normas")).isTrue();
		assertThat(EditalOverview.sameSubject("Conhecimentos Específicos por cargo", "Conhecimentos Específicos")).isTrue();
		assertThat(EditalOverview.sameSubject("LÍNGUA PORTUGUESA", "Língua Portuguesa")).isTrue();
		assertThat(EditalOverview.sameSubject("Língua Inglesa", "Língua Portuguesa")).isFalse();
		assertThat(EditalOverview.sameSubject("Direito", null)).isFalse();
	}

}
