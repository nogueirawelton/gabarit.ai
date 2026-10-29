package com.gabarit.core.simulation;

import com.gabarit.core.extraction.Extraction;
import com.gabarit.core.extraction.ExtractionFinishedEvent;
import com.gabarit.core.extraction.ExtractionService;
import com.gabarit.core.extraction.ExtractionTask;
import com.gabarit.core.shared.BadRequestException;
import com.gabarit.core.shared.NotFoundException;
import com.gabarit.core.study.EditalOverview;
import com.gabarit.core.study.EditalSyllabus;
import com.gabarit.core.study.ExamPaper;
import com.gabarit.core.study.StudyDocumentService;
import com.gabarit.core.study.StudyDocumentService.TargetSyllabus;
import com.gabarit.core.study.StudyTargetService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Questões inéditas por tópico do conteúdo do cargo, no estilo da banca (leitura TOPIC_QUESTIONS,
 * sem documento), e a prática de um tópico (reais das provas relacionadas + geradas).
 */
@Service
public class GenerationService {

	/** Questões geradas por pedido. */
	static final int PER_TOPIC = 5;

	/** Questões numa prática de tópico. */
	static final int MAX_PRACTICE = 20;

	private static final JsonMapper JSON = JsonMapper.builder().build();

	/**
	 * Um tópico do conteúdo (ordinais a partir de 0): quantas questões há para praticar (reais e
	 * geradas) e o pedido de geração, se houve.
	 */
	public record TopicPractice(int subjectOrdinal, int topicOrdinal, int practice, GenerationState generation) {
	}

	/** {@code outdated}: gerada com um prompt mais antigo (gerar de novo usa o atual). */
	public record GenerationState(Extraction.Status status, String failureReason, boolean retryable,
			boolean outdated) {
	}

	/** Saída da leitura TOPIC_QUESTIONS. */
	record Result(List<Item> questions) {

		record Item(String statement, List<ExamPaper.Option> options, String answer,
				String explanation) {
		}

	}

	private final TopicGenerationRepository generations;

	private final QuestionRepository questions;

	private final QuestionTopicRepository links;

	private final TopicCatalog topicCatalog;

	private final TopicService topics;

	private final SimulationService simulations;

	private final StudyDocumentService documents;

	private final StudyTargetService targets;

	private final ExtractionService extractions;

	private final Clock clock;

	GenerationService(TopicGenerationRepository generations, QuestionRepository questions,
			QuestionTopicRepository links, TopicCatalog topicCatalog, TopicService topics,
			SimulationService simulations, StudyDocumentService documents, StudyTargetService targets,
			ExtractionService extractions, Clock clock) {
		this.generations = generations;
		this.questions = questions;
		this.links = links;
		this.topicCatalog = topicCatalog;
		this.topics = topics;
		this.simulations = simulations;
		this.documents = documents;
		this.targets = targets;
		this.extractions = extractions;
		this.clock = clock;
	}

	/**
	 * Cada tópico do conteúdo do cargo com as questões para praticar e a geração. Vazio sem
	 * conteúdo.
	 *
	 * @throws NotFoundException concurso que não é do usuário
	 */
	@Transactional(readOnly = true)
	public List<TopicPractice> overview(String userId, UUID targetId) {
		Optional<TargetSyllabus> syllabus = documents.targetSyllabus(userId, targetId);
		if (syllabus.isEmpty()) {
			return List.of();
		}
		Map<SimulationService.TopicRef, List<UUID>> practice = topics.practiceQuestions(userId, targetId);
		List<TopicGeneration> requested = generations.findByStudyTargetIdAndUserId(targetId, userId);
		Map<UUID, Extraction> readings = requested.isEmpty() ? Map.of()
				: extractions.find(requested.stream().map(TopicGeneration::getExtractionId).toList());
		// tópico (id) → ordinais, só se o conteúdo já foi copiado (geração pedida)
		Map<UUID, int[]> ordinals = requested.isEmpty() ? Map.of()
				: topicCatalog.topics(syllabus.get())
					.stream()
					.collect(Collectors.toMap(SyllabusTopic::getId,
							t -> new int[] { t.getSubject().getOrdinal(), t.getOrdinal() }));
		Map<String, GenerationState> states = new HashMap<>();
		for (TopicGeneration generation : requested) {
			int[] at = ordinals.get(generation.getTopicId());
			Extraction reading = readings.get(generation.getExtractionId());
			if (at != null && reading != null) {
				states.put(at[0] + "|" + at[1], state(reading));
			}
		}
		List<EditalSyllabus.Subject> subjects = syllabus.get().syllabus().subjects();
		List<TopicPractice> result = new ArrayList<>();
		for (int s = 0; s < subjects.size(); s++) {
			List<String> texts = subjects.get(s).topics() == null ? List.of() : subjects.get(s).topics();
			for (int t = 0; t < texts.size(); t++) {
				int count = practice.getOrDefault(new SimulationService.TopicRef(subjects.get(s).name(), texts.get(t)),
						List.of()).size();
				result.add(new TopicPractice(s, t, Math.min(count, MAX_PRACTICE), states.get(s + "|" + t)));
			}
		}
		return result;
	}

	/**
	 * Pede questões inéditas para o tópico, no estilo da banca e no formato da prova do cargo (ou de
	 * novo, se falhou ou o prompt mudou). Mesma banca, formato e tópico (de qualquer pessoa): a mesma
	 * leitura.
	 *
	 * @throws NotFoundException concurso de outro usuário ou tópico que não existe
	 * @throws BadRequestException sem conteúdo do cargo
	 */
	@Transactional
	public GenerationState generate(String userId, UUID targetId, int subjectOrdinal, int topicOrdinal) {
		String board = targets.get(userId, targetId).board();
		TargetSyllabus syllabus = documents.targetSyllabus(userId, targetId)
			.orElseThrow(() -> new BadRequestException("leia o conteúdo programático do seu cargo antes de gerar questões"));
		SyllabusTopic topic = topicCatalog.topics(syllabus)
			.stream()
			.filter(t -> t.getSubject().getOrdinal() == subjectOrdinal && t.getOrdinal() == topicOrdinal)
			.findFirst()
			.orElseThrow(() -> new NotFoundException("tópico %d.%d não existe".formatted(subjectOrdinal + 1,
					topicOrdinal + 1)));
		boolean trueFalse = documents.targetExam(userId, targetId)
			.map(EditalOverview.Exam::answerFormat)
			.map("TRUE_FALSE"::equals)
			.orElse(false);
		List<UUID> papers = List.copyOf(documents.paperReadings(userId, targetId).values());
		Integer optionCount = papers.isEmpty() ? null : questions.mostCommonOptionCount(papers);
		// HashMap: a banca pode ser nula (concurso cadastrado à mão sem banca)
		Map<String, Object> params = new HashMap<>();
		params.put("board", board);
		params.put("answer_format", trueFalse ? "TRUE_FALSE" : "MULTIPLE_CHOICE");
		params.put("options_count", optionCount == null || optionCount < 2 || optionCount > 5 ? 5 : optionCount);
		params.put("subject", topic.getSubject().getName());
		params.put("topic", topic.getText().length() > 4000 ? topic.getText().substring(0, 4000) : topic.getText());
		params.put("count", PER_TOPIC);
		Extraction extraction = extractions.request(ExtractionTask.TOPIC_QUESTIONS, List.of(), params);
		TopicGeneration generation = generations.findByStudyTargetIdAndTopicId(targetId, topic.getId())
			.orElseGet(() -> new TopicGeneration(userId, targetId, topic.getId()));
		generation.request(extraction.getId(), clock.instant());
		generations.save(generation);
		if (extraction.getStatus() == Extraction.Status.DONE) {
			// mesma banca e tópico já gerados (por outra pessoa, ou antes): liga já
			apply(extraction);
		}
		return state(extraction);
	}

	/**
	 * Prática (simulado sem tempo) do tópico: as questões das provas relacionadas ligadas a ele e as
	 * geradas, até {@link #MAX_PRACTICE}.
	 *
	 * @throws BadRequestException sem conteúdo ou sem questão para o tópico
	 */
	@Transactional
	public SimulationService.SimulationView practice(String userId, UUID targetId, int subjectOrdinal,
			int topicOrdinal) {
		targets.get(userId, targetId);
		EditalSyllabus syllabus = documents.targetSyllabus(userId, targetId)
			.orElseThrow(() -> new BadRequestException("leia o conteúdo programático do seu cargo antes de praticar"))
			.syllabus();
		if (subjectOrdinal < 0 || subjectOrdinal >= syllabus.subjects().size()
				|| topicOrdinal < 0 || topicOrdinal >= syllabus.subjects().get(subjectOrdinal).topics().size()) {
			throw new NotFoundException("tópico %d.%d não existe".formatted(subjectOrdinal + 1, topicOrdinal + 1));
		}
		EditalSyllabus.Subject subject = syllabus.subjects().get(subjectOrdinal);
		String text = subject.topics().get(topicOrdinal);
		List<UUID> ids = topics.practiceQuestions(userId, targetId)
			.getOrDefault(new SimulationService.TopicRef(subject.name(), text), List.of())
			.stream()
			.limit(MAX_PRACTICE)
			.toList();
		if (ids.isEmpty()) {
			throw new BadRequestException("ainda não há questões deste tópico: gere questões ou ligue provas relacionadas");
		}
		String title = "Prática · " + (text.length() > 60 ? text.substring(0, 59) + "…" : text);
		return simulations.startPractice(userId, targetId, title, ids);
	}

	/** A geração terminou: grava as questões (uma vez) e liga ao tópico de cada pedido. */
	@EventListener
	void onFinished(ExtractionFinishedEvent event) {
		if (event.task() == ExtractionTask.TOPIC_QUESTIONS && event.status() == Extraction.Status.DONE) {
			apply(extractions.find(List.of(event.extractionId())).get(event.extractionId()));
		}
	}

	private void apply(Extraction extraction) {
		List<TopicGeneration> requested = generations.findByExtractionId(extraction.getId());
		if (requested.isEmpty()) {
			return;
		}
		if (!questions.existsByExtractionId(extraction.getId())) {
			Result result = JSON.readValue(extraction.getResult(), Result.class);
			// a disciplina do conteúdo de quem pediu (o primeiro, se vários conteúdos pediram o mesmo)
			String subject = topicCatalog.subjectOf(requested.getFirst().getTopicId());
			List<Result.Item> items = result.questions() == null ? List.of() : result.questions();
			List<Question> rows = new ArrayList<>();
			for (int i = 0; i < items.size(); i++) {
				Result.Item item = items.get(i);
				boolean trueFalse = item.options() == null || item.options().isEmpty();
				rows.add(Question.generated(extraction.getId(), i, subject,
						trueFalse ? Question.Kind.TRUE_FALSE : Question.Kind.MULTIPLE_CHOICE, item.statement(),
						JSON.writeValueAsString(trueFalse ? List.of() : item.options()),
						item.answer() == null ? null : item.answer().strip().toUpperCase(Locale.ROOT),
						item.explanation()));
			}
			questions.saveAllAndFlush(rows);
		}
		List<Question> generated = questions.findByExtractionIdOrderByOrdinal(extraction.getId());
		List<QuestionTopic> missing = new ArrayList<>();
		for (UUID topic : requested.stream().map(TopicGeneration::getTopicId).distinct().toList()) {
			generated.stream()
				.filter(q -> !links.existsByQuestionIdAndTopicId(q.getId(), topic))
				.forEach(q -> missing.add(new QuestionTopic(q.getId(), topic)));
		}
		links.saveAllAndFlush(missing);
	}

	private static GenerationState state(Extraction extraction) {
		return new GenerationState(extraction.getStatus(), extraction.getFailureReason(), extraction.isRetryable(),
				!extraction.getPromptVersion().equals(extraction.getTask().promptVersion()));
	}

}
