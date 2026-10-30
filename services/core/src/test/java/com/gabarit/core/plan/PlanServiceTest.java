package com.gabarit.core.plan;

import com.gabarit.core.PostgresTestConfiguration;
import com.gabarit.core.plan.PlanService.PlanView;
import com.gabarit.core.plan.PlanService.SessionView;
import com.gabarit.core.shared.BadRequestException;
import com.gabarit.core.shared.NotFoundException;
import com.gabarit.core.simulation.SimulationService;
import com.gabarit.core.simulation.TopicService;
import com.gabarit.core.study.EditalSyllabus;
import com.gabarit.core.study.StudyDocumentService;
import com.gabarit.core.study.StudyDocumentService.TargetSyllabus;
import com.gabarit.core.study.StudyTargetService;
import com.gabarit.core.study.StudyTargetService.StudyTargetSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ PostgresTestConfiguration.class, PlanService.class, PlanServiceTest.FixedClock.class })
class PlanServiceTest {

	// terça-feira
	static final LocalDate TODAY = LocalDate.of(2026, 11, 10);

	/** Relógio que o teste adianta (dias passando sem estudar). */
	static class MutableClock extends Clock {

		Instant now = Instant.parse("2026-11-10T12:00:00Z");

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return now;
		}

	}

	@TestConfiguration(proxyBeanMethods = false)
	static class FixedClock {

		@Bean
		MutableClock clock() {
			return new MutableClock();
		}

	}

	@Autowired
	MutableClock clock;

	static final int[] WEEKDAYS = { 120, 120, 120, 120, 120, 0, 0 };

	@Autowired
	PlanService service;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	TestEntityManager em;

	@MockitoBean
	StudyTargetService targets;

	@MockitoBean
	StudyDocumentService documents;

	@MockitoBean
	TopicService topics;

	@MockitoBean
	SimulationService simulations;

	UUID targetId;

	@BeforeEach
	void concursoComConteudo() {
		// o relógio é do contexto (compartilhado entre os testes): volta para hoje
		clock.now = Instant.parse("2026-11-10T12:00:00Z");
		targetId = UUID.randomUUID();
		jdbc.update("INSERT INTO study_target (id, user_id, title, created_at, updated_at) VALUES (?, 'maria', 'TJ-SP', now(), now())",
				targetId);
		EditalSyllabus syllabus = new EditalSyllabus(true,
				List.of(new EditalSyllabus.Subject("Português", null, List.of("Crase", "Regência", "Concordância"), 1),
						new EditalSyllabus.Subject("Direito", null, List.of("Atos", "Contratos"), 2)));
		given(documents.targetSyllabus("maria", targetId))
			.willReturn(Optional.of(new TargetSyllabus(UUID.randomUUID(), syllabus)));
		given(topics.overview("maria", targetId)).willReturn(new TopicService.TopicsView(true, List.of(), List.of()));
		given(targets.list("maria")).willReturn(List.of(new StudyTargetSummary(targetId, null, "TJ-SP", null, null, null,
				null, null, Instant.parse("2026-11-01T00:00:00Z"), null)));
	}

	@Test
	void montaDeHojeAteAVesperaDaProva() {
		LocalDate exam = TODAY.plusWeeks(5);

		PlanView plan = service.save("maria", targetId, exam, WEEKDAYS);

		assertThat(plan.endDate()).isEqualTo(exam);
		assertThat(plan.dailyMinutes()).containsExactly(120, 120, 120, 120, 120, 0, 0);
		assertThat(plan.sessions()).isNotEmpty()
			.allSatisfy(s -> assertThat(s.date()).isAfterOrEqualTo(TODAY).isBefore(exam));
		assertThat(plan.sessions().getFirst().topics()).first().isEqualTo("Crase");
		assertThat(plan.subjects()).extracting(PlanService.SubjectShare::subject).containsExactly("Português", "Direito");
		assertThat(service.get("maria", targetId)).isPresent();
	}

	@Test
	void refazerMantemOFeitoESeusTopicos() {
		PlanView first = service.save("maria", targetId, TODAY.plusWeeks(5), WEEKDAYS);
		SessionView crase = first.sessions().getFirst();
		service.done("maria", crase.id(), true);
		em.flush();

		PlanView again = service.save("maria", targetId, TODAY.plusWeeks(5), new int[] { 60, 60, 60, 60, 60, 180, 0 });

		assertThat(again.sessions()).filteredOn(SessionView::done).singleElement()
			.satisfies(s -> assertThat(s.id()).isEqualTo(crase.id()));
		// o que foi estudado não volta como estudo
		assertThat(again.sessions()).filteredOn(s -> s.kind().equals("STUDY") && !s.done())
			.allSatisfy(s -> assertThat(s.topics()).doesNotContainAnyElementsOf(crase.topics()));
		// e agora há sábado
		assertThat(again.sessions()).anySatisfy(s -> assertThat(s.date().getDayOfWeek().getValue()).isEqualTo(6));
	}

	@Test
	void agendaDeTodosOsConcursos() {
		service.save("maria", targetId, TODAY.plusWeeks(5), WEEKDAYS);

		var today = service.agenda("maria", TODAY, TODAY);

		assertThat(today).isNotEmpty().allSatisfy(s -> {
			assertThat(s.targetTitle()).isEqualTo("TJ-SP");
			assertThat(s.date()).isEqualTo(TODAY);
		});
		assertThat(service.agenda("joao", TODAY, TODAY)).isEmpty();
		assertThatThrownBy(() -> service.agenda("maria", TODAY, TODAY.plusDays(90))).isInstanceOf(BadRequestException.class);
	}

	@Test
	void validaDataTempoConteudoEDono() {
		assertThatThrownBy(() -> service.save("maria", targetId, TODAY, WEEKDAYS)).isInstanceOf(BadRequestException.class);
		assertThatThrownBy(() -> service.save("maria", targetId, TODAY.plusWeeks(2), new int[7]))
			.isInstanceOf(BadRequestException.class)
			.hasMessageContaining("horas");
		given(documents.targetSyllabus("maria", targetId)).willReturn(Optional.empty());
		assertThatThrownBy(() -> service.save("maria", targetId, TODAY.plusWeeks(2), WEEKDAYS))
			.isInstanceOf(BadRequestException.class)
			.hasMessageContaining("conteúdo programático");

		PlanView plan;
		given(documents.targetSyllabus("maria", targetId)).willReturn(Optional.of(new TargetSyllabus(UUID.randomUUID(),
				new EditalSyllabus(true, List.of(new EditalSyllabus.Subject("Português", null, List.of("Crase"), 1))))));
		plan = service.save("maria", targetId, TODAY.plusWeeks(2), WEEKDAYS);
		assertThatThrownBy(() -> service.done("joao", plan.sessions().getFirst().id(), true))
			.isInstanceOf(NotFoundException.class);
	}

	@Test
	void praticaComAsQuestoesDosTopicosDaSessao() {
		UUID crase1 = UUID.randomUUID();
		UUID crase2 = UUID.randomUUID();
		UUID atos = UUID.randomUUID();
		given(topics.practiceQuestions("maria", targetId))
			.willReturn(Map.of(new SimulationService.TopicRef("Português", "Crase"), List.of(crase1, crase2),
					new SimulationService.TopicRef("Direito", "Atos"), List.of(atos)));

		PlanView plan = service.save("maria", targetId, TODAY.plusWeeks(5), WEEKDAYS);
		SessionView first = plan.sessions().getFirst();
		assertThat(first.topics()).contains("Crase");
		assertThat(first.practice()).isEqualTo(2);

		service.practice("maria", first.id());

		verify(simulations).startPractice("maria", targetId, "Prática · Português", List.of(crase1, crase2));
		assertThatThrownBy(() -> service.practice("joao", first.id())).isInstanceOf(NotFoundException.class);
	}

	@Test
	void planoAtrasadoERefeitoSozinhoDeMadrugada() {
		PlanView first = service.save("maria", targetId, TODAY.plusWeeks(5), WEEKDAYS);
		SessionView done = first.sessions().getFirst();
		service.done("maria", done.id(), true);
		em.flush();
		// três dias sem estudar
		clock.now = clock.now.plus(java.time.Duration.ofDays(3));
		LocalDate later = TODAY.plusDays(3);

		service.replanLate();
		em.flush();
		em.clear();

		PlanView again = service.get("maria", targetId).orElseThrow();
		// nada atrasado: o que não foi feito foi para hoje em diante; o feito ficou
		assertThat(again.sessions()).filteredOn(s -> !s.done()).allSatisfy(s -> assertThat(s.date()).isAfterOrEqualTo(later));
		assertThat(again.sessions()).filteredOn(SessionView::done).singleElement()
			.satisfies(s -> assertThat(s.id()).isEqualTo(done.id()));
	}

}
