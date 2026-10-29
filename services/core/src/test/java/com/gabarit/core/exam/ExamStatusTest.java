package com.gabarit.core.exam;

import com.gabarit.core.shared.ClockConfig;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ExamStatusTest {

	static final LocalDate TODAY = LocalDate.of(2026, 11, 10);

	@Test
	void regras() {
		assertThat(ExamStatus.of(null, null, TODAY)).isEqualTo(ExamStatus.UNKNOWN);
		assertThat(ExamStatus.of(null, TODAY, TODAY)).as("último dia").isEqualTo(ExamStatus.OPEN);
		assertThat(ExamStatus.of(TODAY, TODAY.plusDays(5), TODAY)).as("primeiro dia").isEqualTo(ExamStatus.OPEN);
		assertThat(ExamStatus.of(TODAY.minusDays(3), null, TODAY)).as("começou, sem fim").isEqualTo(ExamStatus.OPEN);
		assertThat(ExamStatus.of(TODAY.plusDays(1), null, TODAY)).isEqualTo(ExamStatus.UPCOMING);
		assertThat(ExamStatus.of(null, TODAY.minusDays(1), TODAY)).isEqualTo(ExamStatus.CLOSED);
	}

	@Test
	void hojeEDoFusoDeBrasiliaNaoDeUtc() {
		// 23h30 do dia 10 em Brasília = 02h30 do dia 11 em UTC: o último dia ainda está aberto
		Clock lateNight = Clock.fixed(Instant.parse("2026-11-11T02:30:00Z"), ClockConfig.ZONE);

		assertThat(LocalDate.now(lateNight)).isEqualTo(TODAY);
		assertThat(ExamStatus.of(null, TODAY, LocalDate.now(lateNight))).isEqualTo(ExamStatus.OPEN);
	}

}
