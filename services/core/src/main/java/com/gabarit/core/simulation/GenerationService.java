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

import java.text.Normalizer;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

	/** Questões do simulado inédito quando o edital não tem quadro de provas. */
	static final int DEFAULT_QUESTIONS = 50;

	/** Questões numa prática de tópico. */
	static final int MAX_PRACTICE = 20;

	private static final JsonMapper JSON = JsonMapper.builder().build();

	/**
	 * Um tópico do conteúdo (ordinais a partir de 0): quantas questões há para praticar (reais e
	 * geradas) e o pedido de geração, se houve.
	 */
	public record TopicPractice(int subjectOrdinal, int topicOrdinal, int practice, GenerationState generation) {
	}

	/**
	 * Simulado inédito do cargo: {@code questions} no total, por disciplina como no quadro de provas;
	 * {@code topics} usados, quantos já têm questões geradas, estão gerando ou falharam.
	 */
	public record MockExamStatus(int questions, List<MockExamSection> sections, int topics, int readyTopics,
			int pendingTopics, int failedTopics) {
	}

	public record MockExamSection(String subject, int questions) {
	}

	/** Um tópico do simulado inédito e quantas questões dele entram. */
	private record Slot(SyllabusTopic topic, int questions) {
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
		Map<String, Object> style = style(userId, targetId);
		SyllabusTopic topic = topicCatalog.topics(syllabus(userId, targetId))
			.stream()
			.filter(t -> t.getSubject().getOrdinal() == subjectOrdinal && t.getOrdinal() == topicOrdinal)
			.findFirst()
			.orElseThrow(() -> new NotFoundException("tópico %d.%d não existe".formatted(subjectOrdinal + 1,
					topicOrdinal + 1)));
		return request(userId, targetId, topic, style);
	}

	/**
	 * O simulado inédito do cargo: a estrutura (disciplinas e questões, como no quadro de provas) e
	 * quantos tópicos já têm questões geradas.
	 *
	 * @throws BadRequestException sem conteúdo do cargo
	 */
	@Transactional
	public MockExamStatus mockExam(String userId, UUID targetId) {
		List<Slot> slots = blueprint(userId, targetId);
		return status(userId, targetId, slots);
	}

	/**
	 * Prepara o simulado inédito: gera as questões dos tópicos que ele usa e ainda não têm (uma vez:
	 * os próximos simulados reaproveitam, e a geração é compartilhada entre quem tem a mesma banca).
	 *
	 * @throws BadRequestException sem conteúdo do cargo
	 */
	@Transactional
	public MockExamStatus prepareMockExam(String userId, UUID targetId) {
		Map<String, Object> style = style(userId, targetId);
		List<Slot> slots = blueprint(userId, targetId);
		Map<UUID, Extraction> requested = requested(userId, targetId);
		slots.stream()
			.map(Slot::topic)
			.filter(t -> {
				Extraction reading = requested.get(t.getId());
				return reading == null || isOutdated(reading)
						|| (reading.getStatus() == Extraction.Status.FAILED && reading.isRetryable());
			})
			.forEach(topic -> request(userId, targetId, topic, style));
		return status(userId, targetId, slots);
	}

	/**
	 * Começa o simulado inédito: as questões geradas de cada tópico da estrutura, na ordem da prova,
	 * primeiro as que a pessoa ainda não viu em outros simulados deste concurso.
	 *
	 * @param timeLimitMinutes nulo = sem limite de tempo
	 * @throws BadRequestException ainda preparando, ou nenhuma questão gerada
	 */
	@Transactional
	public SimulationService.SimulationView startMockExam(String userId, UUID targetId, Integer timeLimitMinutes) {
		String board = targets.get(userId, targetId).board();
		List<Slot> slots = blueprint(userId, targetId);
		MockExamStatus status = status(userId, targetId, slots);
		if (status.pendingTopics() > 0) {
			throw new BadRequestException("o simulado ainda está sendo preparado");
		}
		Map<UUID, List<UUID>> generated = new HashMap<>();
		links.generatedFor(slots.stream().map(s -> s.topic().getId()).toList(), userId)
			.forEach(g -> generated.computeIfAbsent(g.getTopicId(), k -> new ArrayList<>()).add(g.getQuestionId()));
		Set<UUID> seen = new HashSet<>(questions.seenInSimulations(userId, targetId));
		LinkedHashSet<UUID> picked = new LinkedHashSet<>();
		for (Slot slot : slots) {
			List<UUID> pool = new ArrayList<>(generated.getOrDefault(slot.topic().getId(), List.of()));
			pool.removeAll(picked);
			// as ainda não vistas primeiro, mantendo a ordem da geração
			pool.sort(Comparator.comparing(seen::contains));
			picked.addAll(pool.subList(0, Math.min(slot.questions(), pool.size())));
		}
		if (picked.isEmpty()) {
			throw new BadRequestException("prepare o simulado inédito antes de começar");
		}
		String title = "Simulado inédito" + (board == null ? "" : " · " + board);
		return simulations.startChosen(userId, targetId, title, List.copyOf(picked), timeLimitMinutes);
	}

	/**
	 * Estrutura do simulado: do quadro de provas (cada disciplina com o número de questões dela), ou,
	 * sem quadro, {@link #DEFAULT_QUESTIONS} questões repartidas pelo número de tópicos de cada
	 * disciplina. Dentro da disciplina, uma questão por tópico de cada vez, começando pelos que mais
	 * caem nas provas relacionadas (no máximo {@link #PER_TOPIC} por tópico: o que uma geração dá).
	 */
	private List<Slot> blueprint(String userId, UUID targetId) {
		TargetSyllabus syllabus = syllabus(userId, targetId);
		List<SyllabusTopic> all = topicCatalog.topics(syllabus);
		Map<Integer, List<SyllabusTopic>> bySubject = all.stream()
			.collect(Collectors.groupingBy(t -> t.getSubject().getOrdinal(), LinkedHashMap::new, Collectors.toList()));
		List<EditalSyllabus.Subject> subjects = syllabus.syllabus().subjects();
		Map<Integer, Integer> questionsBySubject = new LinkedHashMap<>();
		List<EditalOverview.Section> sections = documents.targetExam(userId, targetId)
			.map(EditalOverview.Exam::sections)
			.orElse(List.of());
		for (EditalOverview.Section section : sections) {
			if (section.questions() == null || section.questions() <= 0) {
				continue;
			}
			String name = normalize(section.subject());
			for (int s = 0; s < subjects.size(); s++) {
				String other = normalize(subjects.get(s).name());
				if (other.equals(name) || (!name.isEmpty() && (other.contains(name) || name.contains(other)))) {
					questionsBySubject.merge(s, section.questions(), Integer::sum);
					break;
				}
			}
		}
		if (questionsBySubject.isEmpty()) {
			// sem quadro (ou nada casou): reparte pelo número de tópicos
			int topicCount = all.size();
			bySubject.forEach((s, topics) -> questionsBySubject.put(s,
					Math.max(1, Math.round((float) DEFAULT_QUESTIONS * topics.size() / topicCount))));
		}
		Map<String, Long> falls = topics.overview(userId, targetId)
			.frequency()
			.stream()
			.collect(Collectors.toMap(f -> f.subjectOrdinal() + "|" + f.topicOrdinal(),
					TopicService.TopicFrequency::questions, Long::sum));
		List<Slot> slots = new ArrayList<>();
		questionsBySubject.forEach((s, total) -> {
			List<SyllabusTopic> ranked = bySubject.getOrDefault(s, List.of())
				.stream()
				.sorted(Comparator
					.comparingLong((SyllabusTopic t) -> -falls.getOrDefault(s + "|" + t.getOrdinal(), 0L))
					.thenComparingInt(SyllabusTopic::getOrdinal))
				.toList();
			if (ranked.isEmpty()) {
				return;
			}
			int[] counts = new int[ranked.size()];
			for (int i = 0; i < Math.min(total, ranked.size() * PER_TOPIC); i++) {
				counts[i % ranked.size()]++;
			}
			// na prova, a disciplina aparece com os tópicos na ordem do edital
			ranked.stream()
				.sorted(Comparator.comparingInt(SyllabusTopic::getOrdinal))
				.forEach(t -> {
					int count = counts[ranked.indexOf(t)];
					if (count > 0) {
						slots.add(new Slot(t, count));
					}
				});
		});
		return slots;
	}

	private MockExamStatus status(String userId, UUID targetId, List<Slot> slots) {
		Map<UUID, Extraction> requested = requested(userId, targetId);
		int ready = 0;
		int pending = 0;
		int failed = 0;
		for (Slot slot : slots) {
			Extraction reading = requested.get(slot.topic().getId());
			if (reading == null) {
				continue;
			}
			switch (reading.getStatus()) {
				case DONE -> ready++;
				case PENDING -> pending++;
				case FAILED -> failed++;
			}
		}
		Map<String, Integer> sections = new LinkedHashMap<>();
		slots.forEach(slot -> sections.merge(slot.topic().getSubject().getName(), slot.questions(), Integer::sum));
		return new MockExamStatus(slots.stream().mapToInt(Slot::questions).sum(),
				sections.entrySet().stream().map(e -> new MockExamSection(e.getKey(), e.getValue())).toList(),
				slots.size(), ready, pending, failed);
	}

	/** Banca do concurso e formato da prova do cargo: o que as questões geradas imitam. */
	private Map<String, Object> style(String userId, UUID targetId) {
		String board = targets.get(userId, targetId).board();
		boolean trueFalse = documents.targetExam(userId, targetId)
			.map(EditalOverview.Exam::answerFormat)
			.map("TRUE_FALSE"::equals)
			.orElse(false);
		List<UUID> papers = List.copyOf(documents.paperReadings(userId, targetId).values());
		Integer optionCount = papers.isEmpty() ? null : questions.mostCommonOptionCount(papers);
		// HashMap: a banca pode ser nula (concurso cadastrado à mão sem banca)
		Map<String, Object> style = new HashMap<>();
		style.put("board", board);
		style.put("answer_format", trueFalse ? "TRUE_FALSE" : "MULTIPLE_CHOICE");
		style.put("options_count", optionCount == null || optionCount < 2 || optionCount > 5 ? 5 : optionCount);
		return style;
	}

	private TargetSyllabus syllabus(String userId, UUID targetId) {
		return documents.targetSyllabus(userId, targetId)
			.orElseThrow(() -> new BadRequestException("leia o conteúdo programático do seu cargo antes de gerar questões"));
	}

	/** Os pedidos de geração do concurso: tópico → leitura. */
	private Map<UUID, Extraction> requested(String userId, UUID targetId) {
		List<TopicGeneration> found = generations.findByStudyTargetIdAndUserId(targetId, userId);
		Map<UUID, Extraction> readings = found.isEmpty() ? Map.of()
				: extractions.find(found.stream().map(TopicGeneration::getExtractionId).toList());
		Map<UUID, Extraction> byTopic = new HashMap<>();
		found.forEach(g -> {
			if (readings.containsKey(g.getExtractionId())) {
				byTopic.put(g.getTopicId(), readings.get(g.getExtractionId()));
			}
		});
		return byTopic;
	}

	/** Pede (ou pede de novo) as questões de um tópico. */
	private GenerationState request(String userId, UUID targetId, SyllabusTopic topic, Map<String, Object> style) {
		Map<String, Object> params = new HashMap<>(style);
		params.put("subject", topic.getSubject().getName());
		params.put("topic", topic.getText().length() > 4000 ? topic.getText().substring(0, 4000) : topic.getText());
		params.put("count", PER_TOPIC);
		Extraction extraction = extractions.request(userId, ExtractionTask.TOPIC_QUESTIONS, List.of(), params);
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

	/** Questões da disciplina no quadro de provas, casada pelo nome (sem acento nem caixa); 0 sem quadro. */
	private static double questionsInExam(String subject, List<EditalOverview.Section> sections) {
		String name = normalize(subject);
		return sections.stream()
			.filter(section -> section.questions() != null)
			.filter(section -> {
				String other = normalize(section.subject());
				return other.equals(name) || (!other.isEmpty() && (other.contains(name) || name.contains(other)));
			})
			.findFirst()
			.map(section -> section.questions() * (section.weight() == null ? 1 : section.weight()))
			.orElse(0.0);
	}

	private static String normalize(String text) {
		return Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFD)
			.replaceAll("\\p{M}", "")
			.toLowerCase(Locale.ROOT)
			.replaceAll("[^a-z0-9]", "");
	}

	private static boolean isOutdated(Extraction extraction) {
		return !extraction.getPromptVersion().equals(extraction.getTask().promptVersion());
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
				isOutdated(extraction));
	}

}
