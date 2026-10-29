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
	}

	@Test
	void vaiParaOWebEmCamelCase() throws IOException {
		String json = JsonMapper.builder().build().writeValueAsString(EditalOverview.parse(result()));

		assertThat(json).contains("\"registrationStart\":\"2025-02-05\"", "\"educationLevel\":\"SUPERIOR\"")
			.doesNotContain("registration_start");
	}

}
