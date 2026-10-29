package com.gabarit.core.exam;

import java.text.Collator;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Concurso com os cargos ativos (os inativos sumiram da fonte), em ordem alfabética. */
public record ExamDetail(UUID id, String title, String uf, String region, String educationLevel, String openings,
		LocalDate registrationStart, LocalDate registrationEnd, String registrationNote, ExamStatus status, String url,
		List<Position> positions) {

	public record Position(UUID id, String name) {
	}

	private static final Comparator<Position> BY_NAME = Comparator.comparing(Position::name, collator());

	static ExamDetail of(Exam exam, LocalDate today) {
		List<Position> positions = exam.getPositions()
			.stream()
			.filter(ExamPosition::isActive)
			.map(p -> new Position(p.getId(), p.getName()))
			.sorted(BY_NAME)
			.toList();
		return new ExamDetail(exam.getId(), exam.getTitle(), exam.getUf(), exam.getRegion(), exam.getEducationLevel(),
				exam.getOpenings(), exam.getRegistrationStart(), exam.getRegistrationEnd(), exam.getRegistrationNote(),
				ExamStatus.of(exam.getRegistrationStart(), exam.getRegistrationEnd(), today), exam.getUrl(), positions);
	}

	private static Collator collator() {
		Collator collator = Collator.getInstance(Locale.forLanguageTag("pt-BR"));
		collator.setStrength(Collator.PRIMARY);
		return collator;
	}

}
