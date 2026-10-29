package com.gabarit.core.study;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/**
 * Conteúdo programático de um cargo lido pela LLM (tarefa EDITAL_SYLLABUS v1 do worker extraction).
 * {@code positionFound} falso: o edital não traz o conteúdo desse cargo (ou a leitura não achou).
 */
public record EditalSyllabus(boolean positionFound, List<Subject> subjects) {

	/** {@code group}: bloco do edital ("Conhecimentos Específicos"), quando agrupa as disciplinas. */
	public record Subject(String name, String group, List<String> topics, Integer page) {
	}

	// o resultado vem em snake_case (contrato do worker); para o web sai em camelCase, como o resto
	private static final JsonMapper RESULT = JsonMapper.builder()
		.propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
		.build();

	/** @param result o JSON de uma leitura EDITAL_SYLLABUS pronta */
	static EditalSyllabus parse(String result) {
		return RESULT.readValue(result, EditalSyllabus.class);
	}

}
