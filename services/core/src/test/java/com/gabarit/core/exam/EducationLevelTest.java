package com.gabarit.core.exam;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EducationLevelTest {

	@Test
	void niveisDoTextoDaFonte() {
		assertThat(EducationLevel.parse("Fundamental / Médio / Técnico / Superior")).containsExactly(
				EducationLevel.FUNDAMENTAL, EducationLevel.MEDIO, EducationLevel.TECNICO, EducationLevel.SUPERIOR);
		assertThat(EducationLevel.parse("Ensino Médio")).containsExactly(EducationLevel.MEDIO);
		assertThat(EducationLevel.parse("ENSINO TECNICO")).containsExactly(EducationLevel.TECNICO);
	}

	@Test
	void semTextoOuDesconhecidoNaoTemNivel() {
		assertThat(EducationLevel.parse(null)).isEmpty();
		assertThat(EducationLevel.parse("")).isEmpty();
		assertThat(EducationLevel.parse("Alfabetizado")).isEmpty();
	}

}
