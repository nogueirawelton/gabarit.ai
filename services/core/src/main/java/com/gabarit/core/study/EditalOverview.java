package com.gabarit.core.study;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

import java.text.Normalizer;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

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

	// conectivos que não mudam o nome da disciplina
	private static final Set<String> CONNECTIVES = Set.of("a", "o", "as", "os", "e", "de", "da", "do", "das", "dos",
			"em", "na", "no", "para", "por");

	/**
	 * Mesma disciplina com nomes um pouco diferentes entre o quadro de provas e o conteúdo
	 * programático ("Conhecimento de Normas" × "Conhecimentos de Normas", "Conhecimentos
	 * Específicos" × "Conhecimentos Específicos por cargo"): sem acento, caixa, conectivos e plural,
	 * um nome contido no outro vale.
	 */
	public static boolean sameSubject(String a, String b) {
		String x = subjectKey(a);
		String y = subjectKey(b);
		return !x.isEmpty() && !y.isEmpty() && (x.equals(y) || x.contains(y) || y.contains(x));
	}

	private static String subjectKey(String name) {
		String plain = Normalizer.normalize(name == null ? "" : name, Normalizer.Form.NFD)
			.replaceAll("\\p{M}", "")
			.toLowerCase(Locale.ROOT);
		return Arrays.stream(plain.split("[^a-z0-9]+"))
			.filter(word -> !word.isEmpty() && !CONNECTIVES.contains(word))
			// plural: "conhecimentos" e "conhecimento" valem o mesmo
			.map(word -> word.length() > 3 && word.endsWith("s") ? word.substring(0, word.length() - 1) : word)
			.collect(Collectors.joining());
	}

	/** @param result o JSON de uma leitura EDITAL_OVERVIEW pronta */
	static EditalOverview parse(String result) {
		return RESULT.readValue(result, EditalOverview.class);
	}

}
