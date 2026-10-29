package com.gabarit.core.exam;

import java.time.LocalDate;

/**
 * Situação das inscrições num dia. Calculada das datas, nunca guardada (mudaria sozinha à meia-noite).
 * As regras têm o mesmo espelho em SQL em {@link ExamSpecifications#status}: mudou aqui, mude lá.
 *
 * <p>A fonte lista concurso já aberto só com a data de fim ("até 30/10"), então sem início conta
 * como já começado.
 */
public enum ExamStatus {

	/** Início depois de hoje. */
	UPCOMING,
	/** Já começou (ou sem início) e o fim é hoje ou depois; ou começou e não tem fim. */
	OPEN,
	/** Fim antes de hoje. */
	CLOSED,
	/** Sem nenhuma data. */
	UNKNOWN;

	static ExamStatus of(LocalDate start, LocalDate end, LocalDate today) {
		if (start == null && end == null) {
			return UNKNOWN;
		}
		if (start != null && start.isAfter(today)) {
			return UPCOMING;
		}
		if (end != null && end.isBefore(today)) {
			return CLOSED;
		}
		return OPEN;
	}

}
