package com.gabarit.core.study;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Prova relacionada lida pela LLM (tarefa EXAM_PAPER v1 do worker extraction): as questões com
 * alternativas, a resposta do gabarito e as anuladas. {@code texts}: textos-base compartilhados,
 * que as questões apontam por {@code textId}.
 */
public record ExamPaper(String variant, boolean answerKeyFound, List<SharedText> texts, List<Question> questions) {

	/** {@code kind}: MULTIPLE_CHOICE, TRUE_FALSE (certo/errado) ou ESSAY (discursiva, sem resposta). */
	public record Question(int number, String subject, String kind, Integer textId, String statement,
			List<Option> options, String answer, boolean annulled, boolean hasImage, Integer page) {
	}

	public record Option(String letter, String text) {
	}

	public record SharedText(int id, String content) {
	}

	/**
	 * O que o web mostra da leitura (as questões em si ficam para o simulado): quantas, quantas
	 * anuladas, quantas sem resposta no gabarito e quantas por disciplina, na ordem da prova.
	 */
	public record Summary(int questions, int annulled, int withoutAnswer, boolean answerKeyFound, String variant,
			List<SubjectCount> bySubject) {
	}

	public record SubjectCount(String subject, int questions) {
	}

	// o resultado vem em snake_case (contrato do worker); para o web sai em camelCase, como o resto
	private static final JsonMapper RESULT = JsonMapper.builder()
		.propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
		.build();

	/** @param result o JSON de uma leitura EXAM_PAPER pronta */
	static ExamPaper parse(String result) {
		return RESULT.readValue(result, ExamPaper.class);
	}

	Summary summary() {
		Map<String, Integer> bySubject = new LinkedHashMap<>();
		questions.forEach(q -> bySubject.merge(q.subject() == null ? "Sem disciplina" : q.subject(), 1, Integer::sum));
		int annulled = (int) questions.stream().filter(Question::annulled).count();
		// discursiva não tem resposta no gabarito; anulada não precisa
		int withoutAnswer = (int) questions.stream()
			.filter(q -> q.answer() == null && !q.annulled() && !"ESSAY".equals(q.kind()))
			.count();
		return new Summary(questions.size(), annulled, withoutAnswer, answerKeyFound, variant,
				bySubject.entrySet().stream().map(e -> new SubjectCount(e.getKey(), e.getValue())).toList());
	}

}
