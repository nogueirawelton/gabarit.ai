package com.gabarit.core.plan;

import com.gabarit.core.plan.PlanBuilder.Draft;
import com.gabarit.core.plan.PlanBuilder.SubjectInput;
import com.gabarit.core.plan.PlanBuilder.TopicInput;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class PlanBuilderTest {

	// segunda-feira
	static final LocalDate START = LocalDate.of(2026, 11, 2);

	// 2 h de segunda a sexta, folga no fim de semana
	static final int[] WEEKDAYS = { 120, 120, 120, 120, 120, 0, 0 };

	static SubjectInput subject(int ordinal, String name, double weight, int topics) {
		return new SubjectInput(ordinal, name, weight,
				IntStream.range(0, topics).mapToObj(i -> new TopicInput(name + " " + (i + 1), 0, null, false)).toList());
	}

	static Map<String, Integer> studyMinutes(List<Draft> drafts) {
		return drafts.stream()
			.filter(d -> d.kind() == PlanSession.Kind.STUDY)
			.collect(Collectors.groupingBy(Draft::subject, Collectors.summingInt(Draft::minutes)));
	}

	@Test
	void tempoProporcionalAoPesoNaProva() {
		List<Draft> drafts = PlanBuilder.build(
				List.of(subject(0, "Português", 30, 6), subject(1, "Direito", 10, 6)), START, START.plusWeeks(6), WEEKDAYS);

		Map<String, Integer> minutes = studyMinutes(drafts);
		assertThat((double) minutes.get("Português") / minutes.get("Direito")).isCloseTo(3.0, within(0.35));
	}

	@Test
	void soNosDiasComTempoEAntesDaProva() {
		LocalDate exam = START.plusWeeks(5);
		List<Draft> drafts = PlanBuilder.build(List.of(subject(0, "Português", 10, 5)), START, exam, WEEKDAYS);

		assertThat(drafts).allSatisfy(d -> {
			assertThat(d.date()).isBefore(exam);
			assertThat(d.date().getDayOfWeek()).isNotIn(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);
		});
		// nenhum dia passa do tempo que a pessoa tem
		assertThat(drafts.stream().collect(Collectors.groupingBy(Draft::date, Collectors.summingInt(Draft::minutes))))
			.allSatisfy((day, total) -> assertThat(total).isLessThanOrEqualTo(120));
	}

	@Test
	void ultimaSemanaSoDeRevisaoComUmSimulado() {
		LocalDate exam = START.plusWeeks(6);
		List<Draft> drafts = PlanBuilder.build(
				List.of(subject(0, "Português", 30, 6), subject(1, "Direito", 10, 6)), START, exam, WEEKDAYS);

		// os 7 últimos dias com tempo: só revisão e simulado
		List<LocalDate> days = drafts.stream().map(Draft::date).distinct().sorted().toList();
		List<LocalDate> finalDays = days.subList(days.size() - 7, days.size());
		assertThat(drafts.stream().filter(d -> finalDays.contains(d.date())))
			.allSatisfy(d -> assertThat(d.kind()).isIn(PlanSession.Kind.REVIEW, PlanSession.Kind.SIMULATION));
		assertThat(drafts.stream().filter(d -> d.kind() == PlanSession.Kind.SIMULATION)).singleElement()
			.satisfies(d -> assertThat(finalDays).contains(d.date()));
	}

	@Test
	void revisaoNoFimDeCadaSemanaComOQueFoiVisto() {
		List<Draft> drafts = PlanBuilder.build(List.of(subject(0, "Português", 10, 20)), START, START.plusWeeks(6),
				WEEKDAYS);

		// sexta da primeira semana: meia hora revendo o que foi estudado nela
		LocalDate friday = START.plusDays(4);
		assertThat(drafts).filteredOn(d -> d.date().equals(friday) && d.kind() == PlanSession.Kind.REVIEW)
			.singleElement()
			.satisfies(d -> {
				assertThat(d.minutes()).isEqualTo(PlanBuilder.WEEKLY_REVIEW);
				assertThat(d.topics()).contains("Português 1");
			});
	}

	@Test
	void topicosNaOrdemDoEditalEOsJaEstudadosFicamDeFora() {
		SubjectInput portugues = new SubjectInput(0, "Português", 10,
				List.of(new TopicInput("Crase", 0, null, true), new TopicInput("Regência", 0, null, false),
						new TopicInput("Concordância", 0, null, false)));
		SubjectInput feito = new SubjectInput(1, "Direito", 50, List.of(new TopicInput("Atos", 0, null, true)));

		List<Draft> drafts = PlanBuilder.build(List.of(portugues, feito), START, START.plusDays(3), WEEKDAYS);

		List<Draft> study = drafts.stream().filter(d -> d.kind() == PlanSession.Kind.STUDY).toList();
		assertThat(study).allSatisfy(d -> assertThat(d.subject()).isEqualTo("Português"));
		assertThat(study.getFirst().topics()).first().isEqualTo("Regência");
		assertThat(study.stream().flatMap(d -> d.topics().stream())).doesNotContain("Crase");
	}

	@Test
	void oQueCaiEOQueErraGanhamMaisTempo() {
		SubjectInput caiMais = new SubjectInput(0, "Português", 10,
				List.of(new TopicInput("Crase", 8, 0.8, false), new TopicInput("Regência", 4, null, false)));
		SubjectInput caiMenos = new SubjectInput(1, "Direito", 10,
				List.of(new TopicInput("Atos", 0, null, false), new TopicInput("Contratos", 0, null, false)));

		Map<String, Integer> minutes = studyMinutes(
				PlanBuilder.build(List.of(caiMais, caiMenos), START, START.plusWeeks(6), WEEKDAYS));

		assertThat(minutes.get("Português")).isGreaterThan(minutes.get("Direito") * 2);
	}

	@Test
	void semTempoNenhumNaoHaPlano() {
		assertThat(PlanBuilder.build(List.of(subject(0, "Português", 10, 5)), START, START.plusWeeks(2),
				new int[] { 0, 0, 0, 0, 0, 0, 0 }))
			.isEmpty();
	}

}
