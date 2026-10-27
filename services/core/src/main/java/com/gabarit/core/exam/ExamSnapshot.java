package com.gabarit.core.exam;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Estado completo de um concurso como a fonte o descreve num dado momento.
 * O evento exam.discovered é traduzido para isto antes de chegar na entidade.
 */
public record ExamSnapshot(
		String source,
		String externalId,
		String title,
		String url,
		String uf,
		String region,
		String educationLevel,
		String openings,
		LocalDate registrationStart,
		LocalDate registrationEnd,
		String registrationNote,
		List<String> positions) {

	public ExamSnapshot {
		Objects.requireNonNull(source, "source");
		Objects.requireNonNull(externalId, "externalId");
		Objects.requireNonNull(title, "title");
		Objects.requireNonNull(url, "url");
		positions = positions == null ? List.of() : List.copyOf(positions);
	}

}
