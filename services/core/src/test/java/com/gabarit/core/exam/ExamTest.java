package com.gabarit.core.exam;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExamTest {

	static final Instant T1 = Instant.parse("2026-10-27T09:00:00Z");
	static final Instant T2 = Instant.parse("2026-10-27T15:00:00Z");

	static ExamSnapshot snapshot(String title, String... positions) {
		return new ExamSnapshot("pciconcursos", "295975", title, "https://example.com/295975", "SP", "SUDESTE",
				"Médio / Superior", "454 vagas", LocalDate.of(2026, 10, 28), LocalDate.of(2026, 12, 10), null,
				List.of(positions));
	}

	static List<String> names(List<ExamPosition> positions) {
		return positions.stream().map(ExamPosition::getName).toList();
	}

	@Test
	void criaComOsDadosECargosDoSnapshot() {
		Exam exam = Exam.create(snapshot("TJ SP", "ESCREVENTE", "OFICIAL"), T1);

		assertThat(exam.getSource()).isEqualTo("pciconcursos");
		assertThat(exam.getExternalId()).isEqualTo("295975");
		assertThat(exam.getTitle()).isEqualTo("TJ SP");
		assertThat(exam.getSourceUpdatedAt()).isEqualTo(T1);
		assertThat(names(exam.getActivePositions())).containsExactly("ESCREVENTE", "OFICIAL");
	}

	@Test
	void eventoMaisNovoAtualiza() {
		Exam exam = Exam.create(snapshot("TJ SP", "ESCREVENTE"), T1);

		boolean applied = exam.apply(snapshot("TJ SP - retificado", "ESCREVENTE"), T2);

		assertThat(applied).isTrue();
		assertThat(exam.getTitle()).isEqualTo("TJ SP - retificado");
		assertThat(exam.getSourceUpdatedAt()).isEqualTo(T2);
	}

	@Test
	void eventoMaisAntigoOuRepetidoEIgnorado() {
		Exam exam = Exam.create(snapshot("novo", "ESCREVENTE"), T2);

		assertThat(exam.apply(snapshot("velho", "OUTRO"), T1)).isFalse();
		assertThat(exam.apply(snapshot("mesmo instante", "OUTRO"), T2)).isFalse();

		assertThat(exam.getTitle()).isEqualTo("novo");
		assertThat(exam.getSourceUpdatedAt()).isEqualTo(T2);
		assertThat(names(exam.getPositions())).containsExactly("ESCREVENTE");
	}

	@Test
	void cargoQueSomeFicaInativoEVoltaAtivoSeReaparecer() {
		Exam exam = Exam.create(snapshot("TJ SP", "ESCREVENTE", "OFICIAL"), T1);

		exam.apply(snapshot("TJ SP", "ESCREVENTE"), T2);
		assertThat(names(exam.getActivePositions())).containsExactly("ESCREVENTE");
		assertThat(names(exam.getPositions())).containsExactly("ESCREVENTE", "OFICIAL");

		exam.apply(snapshot("TJ SP", "ESCREVENTE", "OFICIAL"), T2.plusSeconds(60));
		assertThat(names(exam.getActivePositions())).containsExactly("ESCREVENTE", "OFICIAL");
		assertThat(exam.getPositions()).hasSize(2);
	}

	@Test
	void cargoRepetidoNoSnapshotViraUmSo() {
		Exam exam = Exam.create(snapshot("TJ SP", "ESCREVENTE", "ESCREVENTE"), T1);

		assertThat(names(exam.getPositions())).containsExactly("ESCREVENTE");
	}

	@Test
	void recusaSnapshotDeOutroConcurso() {
		Exam exam = Exam.create(snapshot("TJ SP"), T1);
		ExamSnapshot other = new ExamSnapshot("pciconcursos", "999", "Outro", "https://example.com/999", null, null,
				null, null, null, null, null, List.of());

		assertThatThrownBy(() -> exam.apply(other, T2)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void recusaFimDeInscricaoAntesDoInicio() {
		ExamSnapshot invalid = new ExamSnapshot("pciconcursos", "1", "X", "https://example.com/1", null, null, null,
				null, LocalDate.of(2026, 12, 10), LocalDate.of(2026, 10, 28), null, List.of());

		assertThatThrownBy(() -> Exam.create(invalid, T1)).isInstanceOf(IllegalArgumentException.class);
	}

}
