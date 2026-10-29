package com.gabarit.core.study;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.List;

/**
 * Visão geral do edital lida pela LLM (tarefa EDITAL_OVERVIEW do worker extraction). Sugestão para
 * a pessoa conferir e aplicar no concurso, nunca gravada sozinha. Campo que o edital não traz vem
 * nulo. {@code exams} (quadro de provas) só existe a partir da v2: leitura v1 vem com a lista vazia.
 */
public record EditalOverview(String organ, String board, Integer year, LocalDate registrationStart,
		LocalDate registrationEnd, LocalDate examDate, List<Position> positions, List<Exam> exams) {

	public EditalOverview {
		exams = exams == null ? List.of() : exams;
	}

	/**
	 * {@code openings} e {@code salary} como o edital escreve ("3 + CR", "R$ 8.529,67").
	 * {@code examId}: a prova (em {@code exams}) que vale para o cargo.
	 */
	public record Position(String name, String specialty, String educationLevel, String openings, String salary,
			Integer page, Integer examId) {
	}

	/**
	 * Uma prova objetiva do quadro de provas, comum a vários cargos. {@code answerFormat}:
	 * {@code MULTIPLE_CHOICE} (A–E) ou {@code TRUE_FALSE} (certo/errado); {@code essay}: tem
	 * discursiva ou redação.
	 */
	public record Exam(int id, String appliesTo, String answerFormat, Integer totalQuestions,
			List<Section> sections, boolean essay) {
	}

	/** Uma linha do quadro: disciplina, número de questões e peso (de cada questão ou da disciplina). */
	public record Section(String block, String subject, Integer questions, Double weight) {
	}

	// o resultado vem em snake_case (contrato do worker); para o web sai em camelCase, como o resto
	private static final JsonMapper RESULT = JsonMapper.builder()
		.propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
		.build();

	/** @param result o JSON de uma leitura EDITAL_OVERVIEW pronta */
	static EditalOverview parse(String result) {
		return RESULT.readValue(result, EditalOverview.class);
	}

}
