package com.gabarit.core.simulation;

import com.gabarit.core.extraction.Extraction;
import com.gabarit.core.extraction.ExtractionFinishedEvent;
import com.gabarit.core.extraction.ExtractionService;
import com.gabarit.core.extraction.ExtractionTask;
import com.gabarit.core.shared.BadRequestException;
import com.gabarit.core.shared.NotFoundException;
import com.gabarit.core.study.StudyDocumentService;
import com.gabarit.core.study.StudyDocumentService.PaperQuestions;
import com.gabarit.core.study.StudyDocumentService.TargetSyllabus;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Questões das provas relacionadas → tópicos do conteúdo do cargo (leitura QUESTION_TOPICS, só
 * texto: as questões já lidas e os tópicos vão nos parâmetros). Mostra o que mais cai na banca.
 */
@Service
public class TopicService {

	// o que vai para a LLM de cada questão: o bastante para saber o assunto
	private static final int MAX_STATEMENT = 1500;

	private static final int MAX_OPTION = 200;

	private static final JsonMapper JSON = JsonMapper.builder().build();

	/**
	 * Ligação de questões a tópicos no concurso. {@code syllabusReady}: há conteúdo do cargo para
	 * ligar. {@code frequency}: quantas questões das provas ligadas caem em cada tópico do conteúdo
	 * (ordinais a partir de 0, na ordem do conteúdo que o web mostra).
	 */
	public record TopicsView(boolean syllabusReady, List<PaperTopics> papers, List<TopicFrequency> frequency) {
	}

	/**
	 * A ligação de uma prova. {@code outdated}: feita com outro prompt, outra leitura da prova ou
	 * outro conteúdo do cargo (ligar de novo atualiza).
	 */
	public record PaperTopics(UUID paperId, Extraction.Status status, String failureReason, boolean retryable,
			boolean outdated) {
	}

	public record TopicFrequency(int subjectOrdinal, int topicOrdinal, long questions) {
	}

	/** Saída da leitura QUESTION_TOPICS. */
	record Result(List<Item> classifications) {

		record Item(int ordinal, String topic) {
		}

	}

	private final TopicClassificationRepository classifications;

	private final QuestionRepository questions;

	private final SyllabusTopicRepository topics;

	private final QuestionTopicRepository links;

	private final TopicGenerationRepository generations;

	private final QuestionCatalog questionCatalog;

	private final TopicCatalog topicCatalog;

	private final StudyDocumentService documents;

	private final ExtractionService extractions;

	private final Clock clock;

	TopicService(TopicClassificationRepository classifications, QuestionRepository questions,
			SyllabusTopicRepository topics, QuestionTopicRepository links, TopicGenerationRepository generations,
			QuestionCatalog questionCatalog, TopicCatalog topicCatalog, StudyDocumentService documents,
			ExtractionService extractions, Clock clock) {
		this.classifications = classifications;
		this.questions = questions;
		this.topics = topics;
		this.links = links;
		this.generations = generations;
		this.questionCatalog = questionCatalog;
		this.topicCatalog = topicCatalog;
		this.documents = documents;
		this.extractions = extractions;
		this.clock = clock;
	}

	/**
	 * Pede a ligação das questões da prova relacionada aos tópicos do conteúdo do cargo (ou de novo,
	 * com o que mudou). Mesmas questões e mesmo conteúdo (de qualquer pessoa): a mesma leitura.
	 *
	 * @throws NotFoundException prova ou concurso que não é do usuário
	 * @throws BadRequestException questões ou conteúdo do cargo ainda não lidos
	 */
	@Transactional
	public PaperTopics classify(String userId, UUID targetId, UUID paperId) {
		PaperQuestions exam = documents.paperQuestions(userId, targetId, paperId);
		TargetSyllabus syllabus = documents.targetSyllabus(userId, targetId)
			.orElseThrow(() -> new BadRequestException(
					"leia o conteúdo programático do seu cargo antes de ligar as questões aos tópicos"));
		List<SyllabusTopic> syllabusTopics = topicCatalog.topics(syllabus);
		if (syllabusTopics.isEmpty()) {
			throw new BadRequestException("o conteúdo programático do cargo não tem tópicos");
		}
		Map<String, Object> params = Map.of("exam_reading_id", exam.extractionId().toString(), "syllabus_reading_id",
				syllabus.extractionId().toString(), "topics",
				syllabusTopics.stream()
					.map(t -> Map.of("code", t.code(), "subject", t.getSubject().getName(), "text", cut(t.getText(), 4000)))
					.toList(),
				"questions", questionCatalog.questions(exam).stream().map(TopicService::questionParams).toList());
		Extraction extraction = extractions.request(ExtractionTask.QUESTION_TOPICS, List.of(), params);
		TopicClassification classification = classifications.findByStudyPaperId(paperId)
			.orElseGet(() -> new TopicClassification(userId, targetId, paperId));
		classification.request(exam.extractionId(), syllabus.extractionId(), extraction.getId(), clock.instant());
		classifications.save(classification);
		if (extraction.getStatus() == Extraction.Status.DONE) {
			// mesmas questões e conteúdo já ligados (por outra pessoa, ou antes): grava já
			apply(classification, extraction);
		}
		return paperTopics(classification, extraction, exam.extractionId(), syllabus.extractionId());
	}

	/** @throws NotFoundException concurso que não é do usuário */
	@Transactional(readOnly = true)
	public TopicsView overview(String userId, UUID targetId) {
		Optional<UUID> syllabus = documents.targetSyllabus(userId, targetId).map(TargetSyllabus::extractionId);
		Map<UUID, UUID> paperReadings = documents.paperReadings(userId, targetId);
		List<TopicClassification> found = classifications.findByStudyTargetIdAndUserId(targetId, userId);
		Map<UUID, Extraction> readings = found.isEmpty() ? Map.of()
				: extractions.find(found.stream().map(TopicClassification::getExtractionId).toList());
		List<PaperTopics> papers = found.stream()
			.filter(c -> readings.containsKey(c.getExtractionId()))
			.map(c -> paperTopics(c, readings.get(c.getExtractionId()), paperReadings.get(c.getStudyPaperId()),
					syllabus.orElse(null)))
			.toList();
		List<TopicFrequency> frequency = syllabus.map(syllabusReading -> {
			List<UUID> examReadings = linkedExams(found, readings, paperReadings, syllabusReading);
			return examReadings.isEmpty() ? List.<TopicFrequency>of()
					: links.countByTopic(examReadings, syllabusReading)
						.stream()
						.map(c -> new TopicFrequency(c.getSubjectOrdinal(), c.getTopicOrdinal(), c.getQuestions()))
						.toList();
		}).orElse(List.of());
		return new TopicsView(syllabus.isPresent(), papers, frequency);
	}

	/**
	 * Questões que servem para praticar cada tópico do conteúdo do cargo: as das provas relacionadas
	 * ligadas, com a leitura atual (objetivas, não anuladas e com gabarito), e as geradas para o
	 * tópico; reais primeiro, na ordem das provas. Vazio sem conteúdo.
	 *
	 * @throws NotFoundException concurso que não é do usuário
	 */
	@Transactional(readOnly = true)
	public Map<SimulationService.TopicRef, List<UUID>> practiceQuestions(String userId, UUID targetId) {
		Optional<UUID> syllabus = documents.targetSyllabus(userId, targetId).map(TargetSyllabus::extractionId);
		if (syllabus.isEmpty()) {
			return Map.of();
		}
		List<TopicClassification> found = classifications.findByStudyTargetIdAndUserId(targetId, userId);
		Map<UUID, Extraction> readings = found.isEmpty() ? Map.of()
				: extractions.find(found.stream().map(TopicClassification::getExtractionId).toList());
		List<UUID> sources = new ArrayList<>(
				linkedExams(found, readings, documents.paperReadings(userId, targetId), syllabus.get()));
		generations.findByStudyTargetIdAndUserId(targetId, userId)
			.forEach(g -> sources.add(g.getExtractionId()));
		if (sources.isEmpty()) {
			return Map.of();
		}
		return links.practiceQuestions(sources, syllabus.get())
			.stream()
			.collect(Collectors.groupingBy(q -> new SimulationService.TopicRef(q.getSubject(), q.getTopic()),
					LinkedHashMap::new,
					Collectors.mapping(QuestionTopicRepository.QuestionTopicName::getQuestionId, Collectors.toList())));
	}

	/** Leituras das provas ligadas a este conteúdo, só com a leitura atual de cada prova e ligação pronta. */
	private static List<UUID> linkedExams(List<TopicClassification> found, Map<UUID, Extraction> readings,
			Map<UUID, UUID> paperReadings, UUID syllabusReading) {
		return found.stream()
			.filter(c -> readings.containsKey(c.getExtractionId())
					&& readings.get(c.getExtractionId()).getStatus() == Extraction.Status.DONE
					&& c.getSyllabusReadingId().equals(syllabusReading)
					&& c.getExamReadingId().equals(paperReadings.get(c.getStudyPaperId())))
			.map(TopicClassification::getExamReadingId)
			.distinct()
			.toList();
	}

	/** A leitura terminou: grava as ligações de quem pediu (a leitura é compartilhada: grava uma vez por par). */
	@EventListener
	void onFinished(ExtractionFinishedEvent event) {
		if (event.task() != ExtractionTask.QUESTION_TOPICS || event.status() != Extraction.Status.DONE) {
			return;
		}
		Extraction extraction = extractions.find(List.of(event.extractionId())).get(event.extractionId());
		Map<List<UUID>, TopicClassification> byPair = new LinkedHashMap<>();
		classifications.findByExtractionId(event.extractionId())
			.forEach(c -> byPair.putIfAbsent(List.of(c.getExamReadingId(), c.getSyllabusReadingId()), c));
		byPair.values().forEach(c -> apply(c, extraction));
	}

	/** Troca as ligações das questões da prova com os tópicos do conteúdo pelas desta leitura. */
	private void apply(TopicClassification classification, Extraction extraction) {
		Result result = JSON.readValue(extraction.getResult(), Result.class);
		Map<Integer, UUID> byOrdinal = questions.findByExtractionIdOrderByOrdinal(classification.getExamReadingId())
			.stream()
			.collect(Collectors.toMap(Question::getOrdinal, Question::getId));
		Map<String, UUID> byCode = topics.findBySyllabus(classification.getSyllabusReadingId())
			.stream()
			.collect(Collectors.toMap(SyllabusTopic::code, SyllabusTopic::getId, (a, b) -> a));
		links.unlink(classification.getExamReadingId(), classification.getSyllabusReadingId());
		// código que não existe (a LLM inventou) ou questão repetida: fica de fora
		LinkedHashSet<List<UUID>> pairs = new LinkedHashSet<>();
		for (Result.Item item : result.classifications() == null ? List.<Result.Item>of() : result.classifications()) {
			UUID question = byOrdinal.get(item.ordinal());
			UUID topic = item.topic() == null ? null : byCode.get(item.topic().strip());
			if (question != null && topic != null) {
				pairs.add(List.of(question, topic));
			}
		}
		links.saveAllAndFlush(pairs.stream().map(p -> new QuestionTopic(p.get(0), p.get(1))).toList());
	}

	private static PaperTopics paperTopics(TopicClassification c, Extraction extraction, UUID currentExamReading,
			UUID currentSyllabusReading) {
		boolean outdated = !extraction.getPromptVersion().equals(extraction.getTask().promptVersion())
				|| !c.getExamReadingId().equals(currentExamReading)
				|| !Objects.equals(c.getSyllabusReadingId(), currentSyllabusReading);
		return new PaperTopics(c.getStudyPaperId(), extraction.getStatus(), extraction.getFailureReason(),
				extraction.isRetryable(), outdated);
	}

	private static Map<String, Object> questionParams(Question question) {
		// HashMap: a disciplina pode ser nula
		Map<String, Object> params = new HashMap<>();
		params.put("ordinal", question.getOrdinal());
		params.put("subject", question.getSubject());
		params.put("statement", cut(question.getStatement(), MAX_STATEMENT));
		params.put("options", question.optionList()
			.stream()
			.limit(10)
			.map(o -> cut(o.letter() + ") " + o.text(), MAX_OPTION))
			.toList());
		return params;
	}

	private static String cut(String text, int max) {
		return text.length() <= max ? text : text.substring(0, max - 1) + "…";
	}

}
