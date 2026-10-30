package com.gabarit.core.simulation;

import com.gabarit.core.extraction.Extraction;
import com.gabarit.core.extraction.ExtractionService;
import com.gabarit.core.extraction.ExtractionTask;
import com.gabarit.core.shared.BadRequestException;
import com.gabarit.core.shared.NotFoundException;
import com.gabarit.core.study.EditalSyllabus;
import com.gabarit.core.study.StudyDocumentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Resumo de um tópico do conteúdo do cargo pela LLM (TOPIC_SUMMARY): o que estudar, o que mais cai,
 * pegadinhas, as leis (com o link montado aqui, nunca pela LLM) e leituras. Compartilhado: a leitura
 * é do tópico (disciplina e texto), sem nada da pessoa; não precisa de tabela própria.
 */
@Service
public class SummaryService {

	private static final String CONSTITUTION_URL = "https://www.planalto.gov.br/ccivil_03/constituicao/constituicao.htm";

	// busca do LexML (portal oficial de legislação): sempre existe para o número citado, ao contrário
	// de montar o endereço da norma no Planalto, que muda conforme o ano
	private static final String LEXML_SEARCH = "https://www.lexml.gov.br/busca/search?keyword=";

	private static final JsonMapper RESULT = JsonMapper.builder()
		.propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
		.build();

	/**
	 * O resumo e o estado da leitura: {@code PENDING} → {@code DONE} com o conteúdo, ou
	 * {@code FAILED} ({@code retryable}: pedir de novo pode dar certo).
	 */
	public record SummaryView(Extraction.Status status, String summary, List<String> keyPoints, List<String> pitfalls,
			List<LawView> laws, List<Reading> readings, String failureReason, boolean retryable) {
	}

	/** Norma citada, com o link para o texto (Planalto) ou para a busca dela (LexML). */
	public record LawView(String name, List<String> articles, String url) {
	}

	public record Reading(String author, String title) {
	}

	/** Saída da leitura TOPIC_SUMMARY. */
	record Result(String summary, List<String> keyPoints, List<String> pitfalls, List<Law> laws,
			List<Reading> readings) {

		record Law(String name, String kind, String number, Integer year, List<String> articles) {
		}

	}

	private final StudyDocumentService documents;

	private final ExtractionService extractions;

	SummaryService(StudyDocumentService documents, ExtractionService extractions) {
		this.documents = documents;
		this.extractions = extractions;
	}

	/**
	 * O resumo do tópico, se alguém já pediu (de qualquer concurso com o mesmo tópico).
	 *
	 * @throws NotFoundException concurso de outro usuário ou tópico que não existe
	 */
	@Transactional(readOnly = true)
	public Optional<SummaryView> get(String userId, UUID targetId, int subjectOrdinal, int topicOrdinal) {
		return extractions.lookup(ExtractionTask.TOPIC_SUMMARY, List.of(), params(userId, targetId, subjectOrdinal,
				topicOrdinal))
			.map(SummaryService::view);
	}

	/**
	 * Pede o resumo do tópico (ou de novo, se falhou por erro passageiro). Já pronto ou em andamento:
	 * só devolve.
	 *
	 * @throws NotFoundException concurso de outro usuário ou tópico que não existe
	 * @throws BadRequestException sem conteúdo do cargo, ou limite diário de leituras
	 */
	@Transactional
	public SummaryView request(String userId, UUID targetId, int subjectOrdinal, int topicOrdinal) {
		return view(extractions.request(userId, ExtractionTask.TOPIC_SUMMARY, List.of(),
				params(userId, targetId, subjectOrdinal, topicOrdinal)));
	}

	/** Disciplina e texto do tópico no conteúdo do cargo: as entradas da leitura (iguais para todos). */
	private Map<String, Object> params(String userId, UUID targetId, int subjectOrdinal, int topicOrdinal) {
		EditalSyllabus syllabus = documents.targetSyllabus(userId, targetId)
			.orElseThrow(() -> new BadRequestException("leia o conteúdo programático do seu cargo antes do resumo"))
			.syllabus();
		if (subjectOrdinal < 0 || subjectOrdinal >= syllabus.subjects().size() || topicOrdinal < 0
				|| topicOrdinal >= syllabus.subjects().get(subjectOrdinal).topics().size()) {
			throw new NotFoundException("tópico %d.%d não existe".formatted(subjectOrdinal + 1, topicOrdinal + 1));
		}
		EditalSyllabus.Subject subject = syllabus.subjects().get(subjectOrdinal);
		String topic = subject.topics().get(topicOrdinal);
		return Map.of("subject", subject.name(), "topic", topic.length() > 4000 ? topic.substring(0, 4000) : topic);
	}

	private static SummaryView view(Extraction extraction) {
		if (extraction.getStatus() != Extraction.Status.DONE) {
			return new SummaryView(extraction.getStatus(), null, List.of(), List.of(), List.of(), List.of(),
					extraction.getFailureReason(), extraction.isRetryable());
		}
		Result result = RESULT.readValue(extraction.getResult(), Result.class);
		return new SummaryView(extraction.getStatus(), result.summary(), orEmpty(result.keyPoints()),
				orEmpty(result.pitfalls()),
				orEmpty(result.laws()).stream()
					.map(law -> new LawView(law.name(), orEmpty(law.articles()), url(law)))
					.toList(),
				orEmpty(result.readings()), null, false);
	}

	/** Constituição: o texto no Planalto. Outras: a busca no LexML pelo tipo, número e ano (ou pelo nome). */
	static String url(Result.Law law) {
		if ("CONSTITUTION".equals(law.kind())) {
			return CONSTITUTION_URL;
		}
		String kind = switch (law.kind() == null ? "" : law.kind()) {
			case "LAW" -> "lei";
			case "COMPLEMENTARY_LAW" -> "lei complementar";
			case "DECREE" -> "decreto";
			default -> "";
		};
		String query = law.number() != null && !kind.isEmpty()
				? "%s %s%s".formatted(kind, law.number().replaceAll("[^0-9]", ""),
						law.year() == null ? "" : " " + law.year())
				: law.name();
		return LEXML_SEARCH + URLEncoder.encode(query, StandardCharsets.UTF_8);
	}

	private static <T> List<T> orEmpty(List<T> list) {
		return list == null ? List.of() : list;
	}

}
