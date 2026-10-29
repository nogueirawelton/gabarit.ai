package com.gabarit.core.exam;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Concurso na listagem. Sem os cargos (é o que gera N+1), exceto {@code matchingPositions}: os cargos
 * que o usuário segue, só na busca por cargos (vem numa query a mais por página, não por concurso).
 */
public record ExamSummary(UUID id, String title, String uf, String region, String educationLevel, String openings,
		LocalDate registrationStart, LocalDate registrationEnd, String registrationNote, ExamStatus status, String url,
		List<String> matchingPositions) {

	static ExamSummary of(Exam exam, LocalDate today, List<String> matchingPositions) {
		return new ExamSummary(exam.getId(), exam.getTitle(), exam.getUf(), exam.getRegion(), exam.getEducationLevel(),
				exam.getOpenings(), exam.getRegistrationStart(), exam.getRegistrationEnd(), exam.getRegistrationNote(),
				ExamStatus.of(exam.getRegistrationStart(), exam.getRegistrationEnd(), today), exam.getUrl(),
				matchingPositions);
	}

}
