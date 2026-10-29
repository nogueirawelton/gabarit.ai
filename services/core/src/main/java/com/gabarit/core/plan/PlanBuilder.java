package com.gabarit.core.plan;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A conta do plano de estudos, sem banco: dias e horas disponíveis + disciplinas com peso → sessões.
 *
 * <ul>
 * <li><b>Quanto tempo cada disciplina leva:</b> proporcional à prioridade = peso na prova (questões
 * × peso do quadro; sem quadro, o número de tópicos) × (1 + quanto cai nas provas relacionadas,
 * de 0 a 1) × (1 + quanto a pessoa erra nos simulados, de 0 a 1). Dentro da disciplina, cada tópico
 * na ordem do edital, com mais tempo para o que cai e o que erra.</li>
 * <li><b>Como os dias são preenchidos:</b> blocos de até 1 h, alternando disciplinas (a que está
 * mais atrasada em relação ao seu tempo vai primeiro).</li>
 * <li><b>Revisão:</b> 30 min no último dia de estudo de cada semana (o que foi visto nela) e, no
 * fim, uma fase só de revisão (a última semana, ou 1/5 dos dias em plano curto), com um simulado
 * completo alguns dias antes da prova.</li>
 * </ul>
 */
final class PlanBuilder {

	static final int BLOCK = 60;

	static final int MIN_BLOCK = 25;

	static final int WEEKLY_REVIEW = 30;

	static final int SIMULATION = 240;

	/** {@code weight}: questões × peso no quadro de provas (0: sem quadro). */
	record SubjectInput(int ordinal, String name, double weight, List<TopicInput> topics) {
	}

	/**
	 * {@code falls}: questões das provas relacionadas ligadas ao tópico. {@code missRate}: erradas e
	 * em branco nos simulados, de 0 a 1 (nulo: sem dado). {@code done}: já estudado (sessão feita).
	 */
	record TopicInput(String text, int falls, Double missRate, boolean done) {
	}

	record Draft(LocalDate date, int position, PlanSession.Kind kind, Integer subjectOrdinal, String subject,
			List<String> topics, int minutes) {
	}

	/** Tempo e fila de tópicos de uma disciplina, enquanto o plano é montado. */
	private static final class Subject {

		final SubjectInput input;

		final double score;

		final double target;

		double remaining;

		final Deque<double[]> topicMinutes = new ArrayDeque<>();

		final Deque<String> topicTexts = new ArrayDeque<>();

		Subject(SubjectInput input, double score, double target) {
			this.input = input;
			this.score = score;
			this.target = target;
			this.remaining = target;
		}

	}

	private PlanBuilder() {
	}

	/**
	 * @param start primeiro dia do plano (hoje)
	 * @param end dia da prova (fica de fora)
	 * @param dailyMinutes minutos por dia da semana, segunda primeiro
	 * @return vazio se não há tempo nenhum até a prova
	 */
	static List<Draft> build(List<SubjectInput> subjects, LocalDate start, LocalDate end, int[] dailyMinutes) {
		List<LocalDate> days = start.datesUntil(end).filter(d -> minutes(d, dailyMinutes) > 0).toList();
		if (days.isEmpty()) {
			return List.of();
		}
		int reviewDays = days.size() >= 21 ? 7 : days.size() >= 5 ? Math.max(1, days.size() / 5) : 0;
		List<LocalDate> studyDays = days.subList(0, days.size() - reviewDays);
		List<LocalDate> finalDays = new ArrayList<>(days.subList(days.size() - reviewDays, days.size()));

		// meia hora no último dia de estudo de cada semana (se a semana tem mais de um)
		Set<LocalDate> weeklyReview = new LinkedHashSet<>();
		for (int i = 0; i < studyDays.size(); i++) {
			LocalDate day = studyDays.get(i);
			boolean lastOfWeek = i == studyDays.size() - 1 || !week(studyDays.get(i + 1)).equals(week(day));
			boolean notAlone = i > 0 && week(studyDays.get(i - 1)).equals(week(day));
			if (lastOfWeek && notAlone) {
				weeklyReview.add(day);
			}
		}
		double capacity = studyDays.stream()
			.mapToInt(d -> minutes(d, dailyMinutes) - reserve(d, weeklyReview, dailyMinutes))
			.sum();
		List<Subject> queue = subjects(subjects, capacity);

		List<Draft> drafts = new ArrayList<>();
		List<String> studiedThisWeek = new ArrayList<>();
		LocalDate currentWeek = null;
		for (LocalDate day : studyDays) {
			if (!week(day).equals(currentWeek)) {
				currentWeek = week(day);
				studiedThisWeek.clear();
			}
			int reserve = reserve(day, weeklyReview, dailyMinutes);
			int available = minutes(day, dailyMinutes) - reserve;
			int position = 0;
			Subject last = null;
			while (available >= Math.min(MIN_BLOCK, minutes(day, dailyMinutes))) {
				Subject subject = next(queue, last);
				if (subject == null) {
					break;
				}
				int block = Math.min(BLOCK, available);
				if (subject.remaining < block) {
					block = Math.max(round(subject.remaining), Math.min(MIN_BLOCK, available));
				}
				List<String> topics = take(subject, block);
				drafts.add(new Draft(day, position++, PlanSession.Kind.STUDY, subject.input.ordinal(), subject.input.name(),
						topics, block));
				studiedThisWeek.addAll(topics);
				available -= block;
				last = subject;
			}
			if (queue.stream().allMatch(s -> s.remaining <= 0) && available > 0 && position == 0 && reserve == 0) {
				// tudo estudado antes do fim: o dia vira revisão
				finalDays.add(day);
				continue;
			}
			if (reserve > 0 && !studiedThisWeek.isEmpty()) {
				drafts.add(new Draft(day, position, PlanSession.Kind.REVIEW, null, null,
						List.copyOf(new LinkedHashSet<>(studiedThisWeek)).stream().limit(8).toList(), reserve));
			}
		}
		drafts.addAll(finalPhase(finalDays.stream().sorted().toList(), queue, end, dailyMinutes));
		return drafts;
	}

	/** Fase final: revisão das disciplinas por prioridade (o que mais cai e mais erra) e um simulado completo. */
	private static List<Draft> finalPhase(List<LocalDate> days, List<Subject> subjects, LocalDate end,
			int[] dailyMinutes) {
		List<Draft> drafts = new ArrayList<>();
		if (days.isEmpty() || subjects.isEmpty()) {
			return drafts;
		}
		// simulado: no dia com tempo para ele mais perto de 3 dias antes da prova
		LocalDate simulationDay = days.stream()
			.filter(d -> minutes(d, dailyMinutes) >= 120)
			.min(Comparator.comparingLong(d -> Math.abs(d.until(end).getDays() - 3)))
			.orElse(null);
		List<Subject> byScore = subjects.stream().sorted(Comparator.comparingDouble((Subject s) -> s.score).reversed()).toList();
		int next = 0;
		for (LocalDate day : days) {
			int available = minutes(day, dailyMinutes);
			if (day.equals(simulationDay)) {
				drafts.add(new Draft(day, 0, PlanSession.Kind.SIMULATION, null, null, List.of(),
						Math.min(available, SIMULATION)));
				continue;
			}
			int position = 0;
			while (available >= Math.min(MIN_BLOCK, minutes(day, dailyMinutes))) {
				Subject subject = byScore.get(next++ % byScore.size());
				int block = Math.min(BLOCK, available);
				drafts.add(new Draft(day, position++, PlanSession.Kind.REVIEW, subject.input.ordinal(), subject.input.name(),
						hardest(subject.input), block));
				available -= block;
			}
		}
		return drafts;
	}

	/** Os tópicos que mais caem e mais erra (até 5), para a revisão. */
	private static List<String> hardest(SubjectInput subject) {
		return subject.topics()
			.stream()
			.sorted(Comparator.comparingDouble(PlanBuilder::reviewScore).reversed())
			.limit(5)
			.map(TopicInput::text)
			.toList();
	}

	private static List<Subject> subjects(List<SubjectInput> inputs, double capacity) {
		int maxFalls = inputs.stream()
			.flatMap(s -> s.topics().stream())
			.mapToInt(TopicInput::falls)
			.max()
			.orElse(0);
		int maxSubjectFalls = inputs.stream()
			.mapToInt(s -> s.topics().stream().mapToInt(TopicInput::falls).sum())
			.max()
			.orElse(0);
		List<SubjectInput> open = inputs.stream().filter(s -> s.topics().stream().anyMatch(t -> !t.done())).toList();
		double[] scores = open.stream().mapToDouble(s -> {
			double weight = s.weight() > 0 ? s.weight() : Math.max(1, s.topics().size());
			int falls = s.topics().stream().mapToInt(TopicInput::falls).sum();
			double miss = s.topics()
				.stream()
				.map(TopicInput::missRate)
				.filter(r -> r != null)
				.mapToDouble(Double::doubleValue)
				.average()
				.orElse(0);
			return weight * (1 + (maxSubjectFalls == 0 ? 0 : (double) falls / maxSubjectFalls)) * (1 + miss);
		}).toArray();
		double total = Arrays.stream(scores).sum();
		List<Subject> subjects = new ArrayList<>();
		for (int i = 0; i < open.size(); i++) {
			SubjectInput input = open.get(i);
			Subject subject = new Subject(input, scores[i], total == 0 ? 0 : capacity * scores[i] / total);
			List<TopicInput> pending = input.topics().stream().filter(t -> !t.done()).toList();
			double topicTotal = pending.stream().mapToDouble(t -> topicScore(t, maxFalls)).sum();
			for (TopicInput topic : pending) {
				subject.topicMinutes.add(new double[] { subject.target * topicScore(topic, maxFalls) / topicTotal });
				subject.topicTexts.add(topic.text());
			}
			subjects.add(subject);
		}
		return subjects;
	}

	/** Para a revisão: o que mais cai e mais erra. */
	private static double reviewScore(TopicInput topic) {
		return topic.falls() + (topic.missRate() == null ? 0 : topic.missRate());
	}

	private static double topicScore(TopicInput topic, int maxFalls) {
		return 1 + (maxFalls == 0 ? 0 : (double) topic.falls() / maxFalls)
				+ (topic.missRate() == null ? 0 : topic.missRate());
	}

	/** A disciplina mais atrasada em relação ao seu tempo; se der, outra que não a do bloco anterior. */
	private static Subject next(List<Subject> subjects, Subject last) {
		Comparator<Subject> behind = Comparator.comparingDouble(s -> s.remaining / s.target);
		Subject best = subjects.stream().filter(s -> s.remaining > 0 && s != last).max(behind).orElse(null);
		return best != null ? best : (last != null && last.remaining > 0 ? last : null);
	}

	/** Consome {@code minutes} da fila de tópicos da disciplina; devolve os tópicos tocados. */
	private static List<String> take(Subject subject, int minutes) {
		subject.remaining -= minutes;
		List<String> touched = new ArrayList<>();
		double left = minutes;
		while (left > 0 && !subject.topicMinutes.isEmpty()) {
			double[] head = subject.topicMinutes.peek();
			touched.add(subject.topicTexts.peek());
			if (head[0] <= left) {
				left -= head[0];
				subject.topicMinutes.poll();
				subject.topicTexts.poll();
			}
			else {
				head[0] -= left;
				left = 0;
			}
		}
		if (touched.isEmpty()) {
			// a disciplina passou do tempo por arredondamento: vira revisão dos que mais caem
			touched.addAll(hardest(subject.input));
		}
		return touched;
	}

	private static int reserve(LocalDate day, Set<LocalDate> weeklyReview, int[] dailyMinutes) {
		return weeklyReview.contains(day) ? Math.min(WEEKLY_REVIEW, minutes(day, dailyMinutes) / 2) : 0;
	}

	private static int minutes(LocalDate day, int[] dailyMinutes) {
		return dailyMinutes[day.getDayOfWeek().getValue() - 1];
	}

	private static LocalDate week(LocalDate day) {
		return day.with(DayOfWeek.MONDAY);
	}

	/** Para cima, de 5 em 5 minutos. */
	private static int round(double minutes) {
		return (int) Math.ceil(minutes / 5) * 5;
	}

}
