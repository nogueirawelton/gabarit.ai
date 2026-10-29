package com.gabarit.core.study;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.List;

/**
 * Visão geral do edital lida pela LLM (tarefa EDITAL_OVERVIEW v1 do worker extraction). Sugestão
 * para a pessoa conferir e aplicar no concurso, nunca gravada sozinha. Campo que o edital não traz
 * vem nulo.
 */
public record EditalOverview(String organ, String board, Integer year, LocalDate registrationStart,
		LocalDate registrationEnd, LocalDate examDate, List<Position> positions) {

	/** {@code openings} e {@code salary} como o edital escreve ("3 + CR", "R$ 8.529,67"). */
	public record Position(String name, String specialty, String educationLevel, String openings, String salary,
			Integer page) {
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
