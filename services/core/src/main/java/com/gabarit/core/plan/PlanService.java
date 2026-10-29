package com.gabarit.core.plan;

import com.gabarit.core.shared.BadRequestException;
import com.gabarit.core.shared.NotFoundException;
import com.gabarit.core.simulation.SimulationService;
import com.gabarit.core.simulation.SimulationService.TopicResult;
import com.gabarit.core.simulation.TopicService;
import com.gabarit.core.study.EditalOverview;
import com.gabarit.core.study.EditalSyllabus;
import com.gabarit.core.study.StudyDocumentService;
import com.gabarit.core.study.StudyDocumentService.TargetSyllabus;
import com.gabarit.core.study.StudyTargetService;
import com.gabarit.core.study.StudyTargetService.StudyTargetSummary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Plano de estudos de um concurso: sessões até a véspera da prova, calculadas no core
 * ({@link PlanBuilder}) com o conteúdo do cargo, o quadro de provas, o que cai nas provas
 * relacionadas e o desempenho nos simulados. Refazer mantém as sessões feitas.
 */
@Service
public class PlanService {

	/** Questões de uma prática de sessão (as primeiras, na ordem das provas). */
	public static final int MAX_PRACTICE = 20;

	/** Máximo de minutos de estudo num dia (12 h). */
	public static final int MAX_DAILY_MINUTES = 720;

	/** O agenda de um período cobre no máximo isto (um mês e pouco, com folga). */
	private static final int MAX_AGENDA_DAYS = 62;

	/**
	 * O plano: até quando, as horas por dia da semana (segunda primeiro), quanto tempo cada
	 * disciplina leva e as sessões.
	 */
	public record PlanView(UUID id, UUID studyTargetId, LocalDate endDate, List<Integer> dailyMinutes,
			Instant updatedAt, List<SubjectShare> subjects, List<SessionView> sessions) {
	}

	/** Tempo de estudo da disciplina no plano (feito e no total). */
	public record SubjectShare(String subject, int minutes, int doneMinutes) {
	}

	/**
	 * {@code kind}: STUDY, REVIEW ou SIMULATION. {@code subject} nulo na revisão da semana e no
	 * simulado. {@code practice}: questões das provas relacionadas dos tópicos da sessão (até
	 * {@link #MAX_PRACTICE}).
	 */
	public record SessionView(UUID id, LocalDate date, String kind, String subject, List<String> topics, int minutes,
			boolean done, int practice) {
	}

	/** Sessão na agenda de todos os concursos (com o concurso). */
	public record AgendaSession(UUID id, UUID studyTargetId, String targetTitle, LocalDate date, String kind,
			String subject, List<String> topics, int minutes, boolean done) {
	}

	private final StudyPlanRepository plans;

	private final PlanSessionRepository sessions;

	private final StudyTargetService targets;

	private final StudyDocumentService documents;

	private final TopicService topics;

	private final SimulationService simulations;

	private final Clock clock;

	PlanService(StudyPlanRepository plans, PlanSessionRepository sessions, StudyTargetService targets,
			StudyDocumentService documents, TopicService topics, SimulationService simulations, Clock clock) {
		this.plans = plans;
		this.sessions = sessions;
		this.targets = targets;
		this.documents = documents;
		this.topics = topics;
		this.simulations = simulations;
		this.clock = clock;
	}

	/** @throws NotFoundException concurso que não é do usuário */
	@Transactional(readOnly = true)
	public Optional<PlanView> get(String userId, UUID targetId) {
		targets.get(userId, targetId);
		return plans.findByStudyTargetIdAndUserId(targetId, userId).map(this::view);
	}

	/**
	 * Monta o plano (ou refaz: as sessões não feitas são recalculadas a partir de hoje, as feitas
	 * ficam e os tópicos delas contam como estudados).
	 *
	 * @param endDate a data da prova (o plano vai até a véspera)
	 * @param dailyMinutes minutos por dia da semana, segunda primeiro
	 * @throws NotFoundException concurso que não é do usuário
	 * @throws BadRequestException sem conteúdo do cargo, data que já passou ou nenhum tempo até lá
	 */
	@Transactional
	public PlanView save(String userId, UUID targetId, LocalDate endDate, int[] dailyMinutes) {
		targets.get(userId, targetId);
		LocalDate today = LocalDate.now(clock);
		if (!endDate.isAfter(today)) {
			throw new BadRequestException("a data da prova precisa ser depois de hoje");
		}
		if (dailyMinutes.length != 7 || Arrays.stream(dailyMinutes).anyMatch(m -> m < 0 || m > MAX_DAILY_MINUTES)) {
			throw new BadRequestException("informe os minutos de cada dia da semana (de 0 a %d)".formatted(MAX_DAILY_MINUTES));
		}
		TargetSyllabus syllabus = documents.targetSyllabus(userId, targetId)
			.orElseThrow(() -> new BadRequestException(
					"leia o conteúdo programático do seu cargo antes de montar o plano"));

		StudyPlan plan = plans.findByStudyTargetIdAndUserId(targetId, userId)
			.orElseGet(() -> new StudyPlan(userId, targetId, clock.instant()));
		plan.update(endDate, dailyMinutes, clock.instant());
		plan = plans.save(plan);
		List<PlanSession> done = sessions.findByPlanIdOrderByDateAscPositionAsc(plan.getId())
			.stream()
			.filter(PlanSession::isDone)
			.toList();
		sessions.deleteUndone(plan.getId());

		List<PlanBuilder.Draft> drafts = PlanBuilder.build(inputs(userId, targetId, syllabus, done), today, endDate,
				dailyMinutes);
		if (drafts.isEmpty()) {
			throw new BadRequestException("não há horas de estudo até a prova: marque algum tempo nos dias da semana");
		}
		// a sessão feita hoje fica antes das novas do mesmo dia
		int offset = (int) done.stream().filter(s -> s.getDate().equals(today)).count();
		UUID planId = plan.getId();
		sessions.saveAll(drafts.stream()
			.map(d -> d.date().equals(today) ? new PlanBuilder.Draft(d.date(), d.position() + offset, d.kind(),
					d.subjectOrdinal(), d.subject(), d.topics(), d.minutes()) : d)
			.map(d -> new PlanSession(planId, d))
			.toList());
		sessions.flush();
		return view(plan);
	}

	/**
	 * Marca (ou desmarca) uma sessão como feita.
	 *
	 * @throws NotFoundException sessão de outro usuário
	 */
	@Transactional
	public void done(String userId, UUID sessionId, boolean done) {
		PlanSession session = sessions.findById(sessionId)
			.filter(s -> plans.findById(s.getPlanId()).map(p -> p.getUserId().equals(userId)).orElse(false))
			.orElseThrow(() -> new NotFoundException("sessão %s não encontrada".formatted(sessionId)));
		session.done(done, clock.instant());
	}

	/**
	 * Começa uma prática (simulado sem tempo) com as questões das provas relacionadas ligadas aos
	 * tópicos da sessão.
	 *
	 * @throws NotFoundException sessão de outro usuário
	 * @throws BadRequestException nenhuma questão desses tópicos
	 */
	@Transactional
	public SimulationService.SimulationView practice(String userId, UUID sessionId) {
		PlanSession session = sessions.findById(sessionId)
			.orElseThrow(() -> new NotFoundException("sessão %s não encontrada".formatted(sessionId)));
		StudyPlan plan = plans.findById(session.getPlanId())
			.filter(p -> p.getUserId().equals(userId))
			.orElseThrow(() -> new NotFoundException("sessão %s não encontrada".formatted(sessionId)));
		List<UUID> questions = practiceOf(session, topics.practiceQuestions(userId, plan.getStudyTargetId()));
		if (questions.isEmpty()) {
			throw new BadRequestException(
					"ainda não há questões destes tópicos: ligue as provas relacionadas aos tópicos (passo Provas)");
		}
		String title = "Prática · " + (session.getSubject() != null ? session.getSubject() : "revisão da semana");
		return simulations.startPractice(userId, plan.getStudyTargetId(), title, questions);
	}

	/** Questões dos tópicos da sessão (sem disciplina, na revisão da semana: pelo texto do tópico). */
	private static List<UUID> practiceOf(PlanSession session, Map<SimulationService.TopicRef, List<UUID>> byTopic) {
		if (session.getKind() == PlanSession.Kind.SIMULATION) {
			return List.of();
		}
		Set<String> texts = new HashSet<>(session.topicList());
		return byTopic.entrySet()
			.stream()
			.filter(e -> texts.contains(e.getKey().topic())
					&& (session.getSubject() == null || session.getSubject().equals(e.getKey().subject())))
			.flatMap(e -> e.getValue().stream())
			.distinct()
			.limit(MAX_PRACTICE)
			.toList();
	}

	/**
	 * Sessões de todos os planos do usuário no período (a agenda de Meus concursos).
	 *
	 * @throws BadRequestException período invertido ou longo demais
	 */
	@Transactional(readOnly = true)
	public List<AgendaSession> agenda(String userId, LocalDate from, LocalDate to) {
		if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) > MAX_AGENDA_DAYS) {
			throw new BadRequestException("período inválido (até %d dias)".formatted(MAX_AGENDA_DAYS));
		}
		Map<UUID, StudyPlan> byId = plans.findByUserId(userId)
			.stream()
			.collect(Collectors.toMap(StudyPlan::getId, Function.identity()));
		if (byId.isEmpty()) {
			return List.of();
		}
		Map<UUID, String> titles = targets.list(userId)
			.stream()
			.collect(Collectors.toMap(StudyTargetSummary::id, StudyTargetSummary::title));
		return sessions.findByPlanIdInAndDateBetweenOrderByDateAscPositionAsc(byId.keySet(), from, to)
			.stream()
			.map(s -> {
				UUID targetId = byId.get(s.getPlanId()).getStudyTargetId();
				return new AgendaSession(s.getId(), targetId, titles.get(targetId), s.getDate(), s.getKind().name(),
						s.getSubject(), s.topicList(), s.getMinutes(), s.isDone());
			})
			.toList();
	}

	/** Disciplinas do conteúdo com o peso na prova, o que cai e o que erra, e os tópicos já estudados. */
	private List<PlanBuilder.SubjectInput> inputs(String userId, UUID targetId, TargetSyllabus syllabus,
			List<PlanSession> done) {
		List<EditalOverview.Section> sections = documents.targetExam(userId, targetId)
			.map(EditalOverview.Exam::sections)
			.orElse(List.of());
		Map<String, Integer> falls = topics.overview(userId, targetId)
			.frequency()
			.stream()
			.collect(Collectors.toMap(f -> f.subjectOrdinal() + "|" + f.topicOrdinal(),
					f -> (int) f.questions(), Integer::sum));
		Map<String, Double> missRate = new LinkedHashMap<>();
		for (TopicResult result : simulations.topicPerformance(userId, targetId)) {
			if (result.total() > 0) {
				missRate.put(result.topic().subject() + "|" + result.topic().topic(),
						(double) (result.wrong() + result.blank()) / result.total());
			}
		}
		Set<String> studied = new HashSet<>();
		done.stream()
			.filter(s -> s.getKind() == PlanSession.Kind.STUDY && s.getSubjectOrdinal() != null)
			.forEach(s -> s.topicList().forEach(t -> studied.add(s.getSubjectOrdinal() + "|" + t)));

		List<EditalSyllabus.Subject> subjects = syllabus.syllabus().subjects();
		List<PlanBuilder.SubjectInput> inputs = new ArrayList<>();
		for (int s = 0; s < subjects.size(); s++) {
			EditalSyllabus.Subject subject = subjects.get(s);
			List<String> texts = subject.topics() == null ? List.of() : subject.topics();
			List<PlanBuilder.TopicInput> topicInputs = new ArrayList<>();
			for (int t = 0; t < texts.size(); t++) {
				String text = texts.get(t);
				topicInputs.add(new PlanBuilder.TopicInput(text, falls.getOrDefault(s + "|" + t, 0),
						missRate.get(subject.name() + "|" + text), studied.contains(s + "|" + text)));
			}
			inputs.add(new PlanBuilder.SubjectInput(s, subject.name(), weight(subject.name(), sections), topicInputs));
		}
		return inputs;
	}

	/** Questões × peso da disciplina no quadro de provas, casada pelo nome (sem acento nem caixa). */
	private static double weight(String subject, List<EditalOverview.Section> sections) {
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

	private PlanView view(StudyPlan plan) {
		List<PlanSession> all = sessions.findByPlanIdOrderByDateAscPositionAsc(plan.getId());
		Map<SimulationService.TopicRef, List<UUID>> practice = topics.practiceQuestions(plan.getUserId(),
				plan.getStudyTargetId());
		Map<String, int[]> bySubject = new LinkedHashMap<>();
		all.stream()
			.filter(s -> s.getKind() == PlanSession.Kind.STUDY)
			.forEach(s -> {
				int[] minutes = bySubject.computeIfAbsent(s.getSubject(), k -> new int[2]);
				minutes[0] += s.getMinutes();
				if (s.isDone()) {
					minutes[1] += s.getMinutes();
				}
			});
		return new PlanView(plan.getId(), plan.getStudyTargetId(), plan.getEndDate(),
				Arrays.stream(plan.getDailyMinutes()).boxed().toList(), plan.getUpdatedAt(),
				bySubject.entrySet().stream().map(e -> new SubjectShare(e.getKey(), e.getValue()[0], e.getValue()[1])).toList(),
				all.stream()
					.map(s -> new SessionView(s.getId(), s.getDate(), s.getKind().name(), s.getSubject(), s.topicList(),
							s.getMinutes(), s.isDone(), practiceOf(s, practice).size()))
					.toList());
	}

}
