package com.gabarit.core.simulation;

import com.gabarit.core.shared.BadRequestException;
import com.gabarit.core.shared.NotFoundException;
import com.gabarit.core.study.ExamPaper;
import com.gabarit.core.study.StudyDocumentService;
import com.gabarit.core.study.StudyDocumentService.PaperQuestions;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Simulados: uma prova relacionada lida, feita como prova. A correção é no servidor: enquanto o
 * simulado corre, o web não recebe o gabarito.
 */
@Service
public class SimulationService {

	/** Tempo máximo que dá para escolher (a prova mais longa de concurso passa pouco de 5 h). */
	public static final int MAX_TIME_LIMIT_MINUTES = 600;

	private static final Set<String> TRUE_FALSE = Set.of("C", "E");

	/**
	 * O simulado com as questões. {@code chosen}: a resposta marcada. {@code answer},
	 * {@code annulled} e {@code correct} só depois de terminar ({@code finished}); {@code result}
	 * também.
	 */
	public record SimulationView(UUID id, UUID studyTargetId, String title, Instant startedAt, Instant deadline,
			Instant finishedAt, boolean finished, List<QuestionView> questions, Result result) {
	}

	/**
	 * {@code kind}: MULTIPLE_CHOICE ou TRUE_FALSE (discursiva fica fora do simulado). {@code topic}: o
	 * tópico do conteúdo do cargo (só depois de terminar, e se a prova foi ligada aos tópicos).
	 * {@code generated}: inédita, gerada por IA para o tópico; {@code explanation}: o comentário
	 * dela (só depois de terminar). {@code reported}: a pessoa reportou com problema (fora da nota).
	 */
	public record QuestionView(UUID id, int number, String subject, String kind, String baseText, String statement,
			List<ExamPaper.Option> options, boolean hasImage, Integer page, String chosen, String answer,
			Boolean annulled, Boolean correct, TopicRef topic, boolean generated, String explanation,
			boolean reported) {
	}

	/** Tópico do conteúdo do cargo, com a disciplina dele (como o conteúdo escreve). */
	public record TopicRef(String subject, String topic) {
	}

	/**
	 * Nota. Anulada conta como certa (vale para todos, como na prova). {@code unscored}: sem resposta
	 * no gabarito, fica fora da conta. {@code net}: certas menos erradas, só em prova de certo/errado
	 * (uma errada anula uma certa, como no Cebraspe).
	 */
	public record Result(int total, int correct, int wrong, int blank, int annulled, int unscored, Integer net,
			List<SubjectResult> bySubject, List<TopicResult> byTopic) {
	}

	public record SubjectResult(String subject, int total, int correct, int wrong, int blank) {
	}

	/** Desempenho num tópico do conteúdo do cargo (vazio se a prova não foi ligada aos tópicos). */
	public record TopicResult(TopicRef topic, int total, int correct, int wrong, int blank) {
	}

	/** Simulado na lista do concurso: com a nota quando terminou. */
	public record SimulationSummary(UUID id, String title, Instant startedAt, Instant finishedAt, boolean finished,
			int answered, int total, Integer correct) {
	}

	private final SimulationRepository simulations;

	private final QuestionRepository questions;

	private final SimulationAnswerRepository answers;

	private final StudyDocumentService documents;

	private final QuestionCatalog catalog;

	private final QuestionTopicRepository links;

	private final QuestionReportRepository reports;

	private final Clock clock;

	SimulationService(SimulationRepository simulations, QuestionRepository questions,
			SimulationAnswerRepository answers, StudyDocumentService documents, QuestionCatalog catalog,
			QuestionTopicRepository links, QuestionReportRepository reports, Clock clock) {
		this.simulations = simulations;
		this.questions = questions;
		this.answers = answers;
		this.documents = documents;
		this.catalog = catalog;
		this.links = links;
		this.reports = reports;
		this.clock = clock;
	}

	/**
	 * Começa um simulado da prova relacionada (com as questões já lidas).
	 *
	 * @param timeLimitMinutes nulo = sem limite de tempo
	 * @throws NotFoundException prova que não é do usuário
	 * @throws BadRequestException questões não lidas, ou nenhuma objetiva
	 */
	@Transactional
	public SimulationView start(String userId, UUID targetId, UUID paperId, Integer timeLimitMinutes) {
		PaperQuestions source = documents.paperQuestions(userId, targetId, paperId);
		catalog.questions(source);
		List<Question> objective = objective(source.extractionId());
		if (objective.isEmpty()) {
			throw new BadRequestException("esta prova não tem questões objetivas para o simulado");
		}
		Simulation simulation = simulations.save(new Simulation(userId, targetId, paperId, source.extractionId(),
				source.title(), clock.instant(), timeLimitMinutes == null ? null : Duration.ofMinutes(timeLimitMinutes)));
		return view(simulation, objective, Map.of());
	}

	/**
	 * Começa um simulado de prática com as questões dadas, na ordem, sem limite de tempo (ex.: as
	 * questões dos tópicos de uma sessão do plano). Quem chama já conferiu o concurso.
	 *
	 * @throws BadRequestException nenhuma questão
	 */
	@Transactional
	public SimulationView startPractice(String userId, UUID targetId, String title, List<UUID> questionIds) {
		return startChosen(userId, targetId, title, questionIds, null);
	}

	/**
	 * Começa um simulado com as questões dadas, na ordem (ex.: o simulado inédito, montado com
	 * questões geradas). Quem chama já conferiu o concurso.
	 *
	 * @param timeLimitMinutes nulo = sem limite de tempo
	 * @throws BadRequestException nenhuma questão
	 */
	@Transactional
	public SimulationView startChosen(String userId, UUID targetId, String title, List<UUID> questionIds,
			Integer timeLimitMinutes) {
		if (questionIds.isEmpty()) {
			throw new BadRequestException("não há questões para o simulado");
		}
		Simulation simulation = simulations.saveAndFlush(new Simulation(userId, targetId, null, null, title,
				clock.instant(), timeLimitMinutes == null ? null : Duration.ofMinutes(timeLimitMinutes)));
		for (int i = 0; i < questionIds.size(); i++) {
			questions.addToSimulation(simulation.getId(), questionIds.get(i), i);
		}
		return view(simulation, questionsOf(simulation), Map.of());
	}

	/** @throws NotFoundException não existe ou é de outro usuário */
	@Transactional(readOnly = true)
	public SimulationView get(String userId, UUID id) {
		Simulation simulation = find(userId, id);
		return view(simulation, questionsOf(simulation), chosen(simulation.getId()));
	}

	/** Simulados do concurso, mais recentes primeiro. */
	@Transactional(readOnly = true)
	public List<SimulationSummary> list(String userId, UUID targetId) {
		List<Simulation> found = simulations.findByStudyTargetIdAndUserIdOrderByStartedAtDesc(targetId, userId);
		Map<UUID, List<Question>> bySimulation = found.stream()
			.collect(Collectors.toMap(Simulation::getId, this::questionsOf));
		Map<UUID, Map<UUID, String>> chosenBySimulation = answers
			.findBySimulationIdIn(found.stream().map(Simulation::getId).toList())
			.stream()
			.collect(Collectors.groupingBy(SimulationAnswer::getSimulationId,
					Collectors.toMap(SimulationAnswer::getQuestionId, SimulationAnswer::getAnswer)));
		Instant now = clock.instant();
		return found.stream().map(simulation -> {
			List<Question> objective = bySimulation.get(simulation.getId());
			Map<UUID, String> chosen = chosenBySimulation.getOrDefault(simulation.getId(), Map.of());
			boolean finished = simulation.isFinished(now);
			return new SimulationSummary(simulation.getId(), simulation.getTitle(), simulation.getStartedAt(),
					simulation.finishedAt(now), finished, chosen.size(), objective.size(),
					finished ? result(objective, chosen, Map.of(), reported(userId, objective)).correct() : null);
		}).toList();
	}

	/**
	 * Desempenho por tópico do conteúdo do cargo, somando os simulados entregues do concurso (para o
	 * plano). Só questões de provas ligadas aos tópicos.
	 */
	@Transactional(readOnly = true)
	public List<TopicResult> topicPerformance(String userId, UUID targetId) {
		Instant now = clock.instant();
		Map<TopicRef, int[]> sum = new LinkedHashMap<>();
		for (Simulation simulation : simulations.findByStudyTargetIdAndUserIdOrderByStartedAtDesc(targetId, userId)) {
			if (!simulation.isFinished(now)) {
				continue;
			}
			List<Question> objective = questionsOf(simulation);
			Map<UUID, TopicRef> topics = topics(simulation, objective);
			if (topics.isEmpty()) {
				continue;
			}
			result(objective, chosen(simulation.getId()), topics, reported(userId, objective)).byTopic()
				.forEach(t -> {
					int[] counts = sum.computeIfAbsent(t.topic(), k -> new int[4]);
					counts[0] += t.total();
					counts[1] += t.correct();
					counts[2] += t.wrong();
					counts[3] += t.blank();
				});
		}
		return sum.entrySet()
			.stream()
			.map(e -> new TopicResult(e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2], e.getValue()[3]))
			.toList();
	}

	/**
	 * Reporta uma questão do simulado com problema (de novo: atualiza o motivo). Para a pessoa, ela sai
	 * da nota e não volta na prática; reportada por {@link QuestionReport#REPORTS_TO_HIDE} pessoas, sai
	 * da prática de todos.
	 *
	 * @throws NotFoundException simulado de outro usuário ou questão que não está nele
	 */
	@Transactional
	public void report(String userId, UUID id, UUID questionId, QuestionReport.Reason reason, String comment) {
		Simulation simulation = find(userId, id);
		boolean inSimulation = questionsOf(simulation).stream().anyMatch(q -> q.getId().equals(questionId));
		if (!inSimulation) {
			throw new NotFoundException("questão %s não está neste simulado".formatted(questionId));
		}
		QuestionReport report = reports.findByQuestionIdAndUserId(questionId, userId)
			.orElseGet(() -> new QuestionReport(questionId, userId));
		report.report(id, reason, comment == null || comment.isBlank() ? null : comment.strip(), clock.instant());
		reports.save(report);
	}

	/**
	 * Marca (ou desmarca, com {@code answer} nulo) a resposta de uma questão.
	 *
	 * @throws NotFoundException simulado de outro usuário ou questão de outra prova
	 * @throws BadRequestException simulado terminado ou resposta que não é alternativa da questão
	 */
	@Transactional
	public void answer(String userId, UUID id, UUID questionId, String answer) {
		Simulation simulation = find(userId, id);
		Instant now = clock.instant();
		if (simulation.isFinished(now)) {
			throw new BadRequestException("o simulado já terminou");
		}
		Question question = questionsOf(simulation).stream()
			.filter(q -> q.getId().equals(questionId))
			.findFirst()
			.orElseThrow(() -> new NotFoundException("questão %s não está neste simulado".formatted(questionId)));
		var existing = answers.findBySimulationIdAndQuestionId(id, questionId);
		if (answer == null || answer.isBlank()) {
			existing.ifPresent(answers::delete);
			return;
		}
		String normalized = answer.strip().toUpperCase(Locale.ROOT);
		if (!validAnswers(question).contains(normalized)) {
			throw new BadRequestException("resposta inválida para a questão %d: %s".formatted(question.getNumber(), answer));
		}
		existing.ifPresentOrElse(a -> a.change(normalized, now),
				() -> answers.save(new SimulationAnswer(id, questionId, normalized, now)));
	}

	/** Entrega (repetir não muda a hora da entrega). */
	@Transactional
	public SimulationView finish(String userId, UUID id) {
		Simulation simulation = find(userId, id);
		simulation.finish(clock.instant());
		return view(simulation, questionsOf(simulation), chosen(simulation.getId()));
	}

	private List<Question> objective(UUID extractionId) {
		return questions.findByExtractionIdOrderByOrdinal(extractionId)
			.stream()
			.filter(q -> q.getKind() != Question.Kind.ESSAY)
			.toList();
	}

	private Map<UUID, String> chosen(UUID simulationId) {
		return answers.findBySimulationId(simulationId)
			.stream()
			.collect(Collectors.toMap(SimulationAnswer::getQuestionId, SimulationAnswer::getAnswer));
	}

	private Simulation find(String userId, UUID id) {
		return simulations.findByIdAndUserId(id, userId)
			.orElseThrow(() -> new NotFoundException("simulado %s não encontrado".formatted(id)));
	}

	private SimulationView view(Simulation simulation, List<Question> objective, Map<UUID, String> chosen) {
		Instant now = clock.instant();
		boolean finished = simulation.isFinished(now);
		Map<UUID, TopicRef> topics = finished ? topics(simulation, objective) : Map.of();
		Set<UUID> reported = reported(simulation.getUserId(), objective);
		List<QuestionView> views = objective.stream().map(q -> {
			String mine = chosen.get(q.getId());
			return new QuestionView(q.getId(), q.getNumber(), q.getSubject(), q.getKind().name(), q.getBaseText(),
					q.getStatement(), options(q), q.hasImage(), q.getPage(), mine, finished ? q.getAnswer() : null,
					finished ? q.isAnnulled() : null,
					finished && !reported.contains(q.getId()) ? isCorrect(q, mine) : null, topics.get(q.getId()),
					q.isGenerated(), finished ? q.getExplanation() : null, reported.contains(q.getId()));
		}).toList();
		return new SimulationView(simulation.getId(), simulation.getStudyTargetId(), simulation.getTitle(),
				simulation.getStartedAt(), simulation.getDeadline(), simulation.finishedAt(now), finished, views,
				finished ? result(objective, chosen, topics, reported) : null);
	}

	/** As questões objetivas do simulado: a prova inteira, ou as escolhidas (prática). */
	private List<Question> questionsOf(Simulation simulation) {
		if (simulation.getExtractionId() != null) {
			return objective(simulation.getExtractionId());
		}
		return questions.findBySimulation(simulation.getId())
			.stream()
			.filter(q -> q.getKind() != Question.Kind.ESSAY)
			.toList();
	}

	/** O tópico de cada questão no conteúdo atual do cargo do concurso (prova não ligada: vazio). */
	private Map<UUID, TopicRef> topics(Simulation simulation, List<Question> objective) {
		if (objective.isEmpty()) {
			return Map.of();
		}
		List<UUID> ids = objective.stream().map(Question::getId).toList();
		return documents.targetSyllabus(simulation.getUserId(), simulation.getStudyTargetId())
			.map(syllabus -> links.topicsOf(ids, syllabus.extractionId())
				.stream()
				.collect(Collectors.toMap(QuestionTopicRepository.QuestionTopicName::getQuestionId,
						t -> new TopicRef(t.getSubject(), t.getTopic()), (a, b) -> a)))
			.orElse(Map.of());
	}

	/** Certa, errada ou nula (em branco, ou sem resposta no gabarito). Anulada conta como certa. */
	private static Boolean isCorrect(Question question, String chosen) {
		if (question.isAnnulled()) {
			return true;
		}
		if (chosen == null || question.getAnswer() == null) {
			return null;
		}
		return chosen.equalsIgnoreCase(question.getAnswer().strip());
	}

	/** Das questões, as que a pessoa reportou com problema (ficam fora da nota). */
	private Set<UUID> reported(String userId, List<Question> objective) {
		return objective.isEmpty() ? Set.of()
				: new HashSet<>(reports.reportedBy(userId, objective.stream().map(Question::getId).toList()));
	}

	private static Result result(List<Question> objective, Map<UUID, String> chosen, Map<UUID, TopicRef> topics,
			Set<UUID> reported) {
		Map<String, int[]> bySubject = new LinkedHashMap<>();
		Map<TopicRef, int[]> byTopic = new LinkedHashMap<>();
		int correct = 0, wrong = 0, blank = 0, annulled = 0, unscored = 0;
		for (Question q : objective) {
			String mine = chosen.get(q.getId());
			// total, certas, erradas, em branco
			int[] subject = bySubject.computeIfAbsent(q.getSubject() == null ? "Sem disciplina" : q.getSubject(),
					s -> new int[4]);
			// questão sem tópico (prova não ligada, ou assunto fora do cargo) só conta na disciplina
			int[] topic = topics.containsKey(q.getId()) ? byTopic.computeIfAbsent(topics.get(q.getId()), t -> new int[4])
					: new int[4];
			subject[0]++;
			topic[0]++;
			// índice: 1 certas, 2 erradas, 3 em branco
			int outcome;
			if (q.isAnnulled()) {
				annulled++;
				correct++;
				outcome = 1;
			}
			else if (q.getAnswer() == null || reported.contains(q.getId())) {
				// sem gabarito, ou reportada com problema: fora da nota
				unscored++;
				outcome = 0;
			}
			else if (mine == null) {
				blank++;
				outcome = 3;
			}
			else if (mine.equalsIgnoreCase(q.getAnswer().strip())) {
				correct++;
				outcome = 1;
			}
			else {
				wrong++;
				outcome = 2;
			}
			if (outcome > 0) {
				subject[outcome]++;
				topic[outcome]++;
			}
		}
		boolean trueFalse = !objective.isEmpty() && objective.stream().allMatch(q -> q.getKind() == Question.Kind.TRUE_FALSE);
		List<SubjectResult> subjects = new ArrayList<>();
		bySubject.forEach((name, c) -> subjects.add(new SubjectResult(name, c[0], c[1], c[2], c[3])));
		// onde mais errou primeiro (erradas, depois em branco)
		List<TopicResult> topicResults = byTopic.entrySet()
			.stream()
			.map(e -> new TopicResult(e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2], e.getValue()[3]))
			.sorted(Comparator.comparingInt(TopicResult::wrong).thenComparingInt(TopicResult::blank).reversed())
			.toList();
		return new Result(objective.size(), correct, wrong, blank, annulled, unscored,
				trueFalse ? correct - wrong : null, subjects, topicResults);
	}

	private static List<ExamPaper.Option> options(Question question) {
		return question.optionList();
	}

	private static Collection<String> validAnswers(Question question) {
		return question.getKind() == Question.Kind.TRUE_FALSE ? TRUE_FALSE
				: options(question).stream().map(o -> o.letter().strip().toUpperCase(Locale.ROOT)).toList();
	}

}
