package com.gabarit.core.simulation;

import com.gabarit.core.PostgresTestConfiguration;
import com.gabarit.core.shared.BadRequestException;
import com.gabarit.core.shared.NotFoundException;
import com.gabarit.core.simulation.SimulationService.QuestionView;
import com.gabarit.core.simulation.SimulationService.SimulationView;
import com.gabarit.core.study.ExamPaper;
import com.gabarit.core.study.StudyDocumentService;
import com.gabarit.core.study.StudyDocumentService.PaperQuestions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

/**
 * Postgres real. Sem a transação de teste: a cópia das questões roda em transação própria e precisa
 * ver o concurso, a prova e a leitura já gravados (cada teste usa ids novos).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ PostgresTestConfiguration.class, SimulationService.class, QuestionCatalog.class, SimulationServiceTest.Clocks.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SimulationServiceTest {

	/** Relógio que o teste adianta (tempo de prova). */
	public static class MutableClock extends Clock {

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
	public static class Clocks {

		@Bean
		MutableClock clock() {
			return new MutableClock();
		}

	}

	// 1: certa é B; 2: certa é C; 3: anulada; 4: discursiva (fica fora do simulado)
	public static final String PAPER = """
			{"variant": "Tipo 1", "answer_key_found": true,
			 "texts": [{"id": 1, "content": "Texto I"}],
			 "questions": [
			  {"number": 1, "subject": "Português", "kind": "MULTIPLE_CHOICE", "text_id": 1, "statement": "Q1",
			   "options": [{"letter": "A", "text": "a"}, {"letter": "B", "text": "b"}, {"letter": "C", "text": "c"}],
			   "answer": "B", "annulled": false, "has_image": false, "page": 1},
			  {"number": 2, "subject": "Português", "kind": "MULTIPLE_CHOICE", "text_id": null, "statement": "Q2",
			   "options": [{"letter": "A", "text": "a"}, {"letter": "B", "text": "b"}, {"letter": "C", "text": "c"}],
			   "answer": "C", "annulled": false, "has_image": false, "page": 1},
			  {"number": 3, "subject": "Direito", "kind": "MULTIPLE_CHOICE", "text_id": null, "statement": "Q3",
			   "options": [{"letter": "A", "text": "a"}, {"letter": "B", "text": "b"}],
			   "answer": null, "annulled": true, "has_image": false, "page": 2},
			  {"number": 4, "subject": null, "kind": "ESSAY", "text_id": null, "statement": "Redija",
			   "options": [], "answer": null, "annulled": false, "has_image": false, "page": 3}]}
			""";

	static final JsonMapper RESULT = JsonMapper.builder()
		.propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
		.build();

	@Autowired
	SimulationService service;

	@Autowired
	MutableClock clock;

	@Autowired
	JdbcTemplate jdbc;

	@MockitoBean
	StudyDocumentService documents;

	UUID targetId;

	UUID paperId;

	UUID extractionId;

	@BeforeEach
	void provaLida() {
		targetId = UUID.randomUUID();
		paperId = UUID.randomUUID();
		extractionId = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO study_target (id, user_id, title, created_at, updated_at)
				VALUES (?, 'maria', 'TJ-SP', now(), now())""", targetId);
		jdbc.update("""
				INSERT INTO extraction (id, task, inputs_hash, prompt_version, inputs, status, created_at, requested_at)
				VALUES (?, 'EXAM_PAPER', ?, 'v1', '{}', 'DONE', now(), now())""", extractionId, extractionId.toString());
		jdbc.update("""
				INSERT INTO study_paper (id, user_id, study_target_id, year, variant, answers_included, created_at,
				  extraction_id)
				VALUES (?, 'maria', ?, 2019, 'Tipo 1', true, now(), ?)""", paperId, targetId, extractionId);
		given(documents.paperQuestions("maria", targetId, paperId)).willReturn(
				new PaperQuestions(extractionId, "Prova de 2019 · Tipo 1", RESULT.readValue(PAPER, ExamPaper.class)));
	}

	QuestionView number(SimulationView simulation, int number) {
		return simulation.questions().stream().filter(q -> q.number() == number).findFirst().orElseThrow();
	}

	@Test
	void comecaComAsObjetivasESemOGabarito() {
		SimulationView started = service.start("maria", targetId, paperId, null);

		assertThat(started.title()).isEqualTo("Prova de 2019 · Tipo 1");
		assertThat(started.deadline()).isNull();
		assertThat(started.finished()).isFalse();
		assertThat(started.result()).isNull();
		// a discursiva fica de fora
		assertThat(started.questions()).extracting(QuestionView::number).containsExactly(1, 2, 3);
		assertThat(number(started, 1)).satisfies(q -> {
			assertThat(q.baseText()).isEqualTo("Texto I");
			assertThat(q.options()).extracting(ExamPaper.Option::letter).containsExactly("A", "B", "C");
			// gabarito só depois de entregar
			assertThat(q.answer()).isNull();
			assertThat(q.annulled()).isNull();
		});
		// a cópia guarda a prova inteira, uma vez só (o segundo simulado usa a mesma)
		service.start("maria", targetId, paperId, 60);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM question WHERE extraction_id = ?", Integer.class,
				extractionId))
			.isEqualTo(4);
	}

	@Test
	void corrigeNaEntregaComAnuladaComoCerta() {
		SimulationView started = service.start("maria", targetId, paperId, null);
		service.answer("maria", started.id(), number(started, 1).id(), " b ");
		service.answer("maria", started.id(), number(started, 2).id(), "A");

		SimulationView finished = service.finish("maria", started.id());

		assertThat(finished.finished()).isTrue();
		assertThat(finished.finishedAt()).isEqualTo(clock.now);
		assertThat(number(finished, 1)).satisfies(q -> {
			assertThat(q.chosen()).isEqualTo("B");
			assertThat(q.answer()).isEqualTo("B");
			assertThat(q.correct()).isTrue();
		});
		assertThat(number(finished, 2).correct()).isFalse();
		assertThat(number(finished, 3).correct()).as("anulada").isTrue();
		assertThat(finished.result()).satisfies(r -> {
			assertThat(r.total()).isEqualTo(3);
			assertThat(r.correct()).isEqualTo(2);
			assertThat(r.wrong()).isEqualTo(1);
			assertThat(r.annulled()).isEqualTo(1);
			assertThat(r.net()).as("só em certo/errado").isNull();
			assertThat(r.bySubject()).containsExactly(new SimulationService.SubjectResult("Português", 2, 1, 1, 0),
					new SimulationService.SubjectResult("Direito", 1, 1, 0, 0));
		});
		assertThatThrownBy(() -> service.answer("maria", started.id(), number(started, 2).id(), "C"))
			.isInstanceOf(BadRequestException.class)
			.hasMessageContaining("terminou");
		assertThat(service.list("maria", targetId)).singleElement().satisfies(s -> {
			assertThat(s.answered()).isEqualTo(2);
			assertThat(s.correct()).isEqualTo(2);
		});
	}

	@Test
	void desmarcarApagaAResposta() {
		SimulationView started = service.start("maria", targetId, paperId, null);
		UUID first = number(started, 1).id();
		service.answer("maria", started.id(), first, "A");
		service.answer("maria", started.id(), first, "C");
		assertThat(number(service.get("maria", started.id()), 1).chosen()).isEqualTo("C");

		service.answer("maria", started.id(), first, null);

		assertThat(number(service.get("maria", started.id()), 1).chosen()).isNull();
	}

	@Test
	void respostaForaDasAlternativasOuDeOutroLugarNaoEntra() {
		SimulationView started = service.start("maria", targetId, paperId, null);
		UUID third = number(started, 3).id();

		// a questão 3 só tem A e B
		assertThatThrownBy(() -> service.answer("maria", started.id(), third, "C"))
			.isInstanceOf(BadRequestException.class);
		assertThatThrownBy(() -> service.answer("maria", started.id(), UUID.randomUUID(), "A"))
			.isInstanceOf(NotFoundException.class);
		assertThatThrownBy(() -> service.answer("joao", started.id(), third, "A")).isInstanceOf(NotFoundException.class);
		assertThatThrownBy(() -> service.get("joao", started.id())).isInstanceOf(NotFoundException.class);
	}

	@Test
	void tempoEsgotadoTerminaNoPrazo() {
		Instant start = clock.now;
		SimulationView started = service.start("maria", targetId, paperId, 30);
		assertThat(started.deadline()).isEqualTo(start.plus(Duration.ofMinutes(30)));
		UUID first = number(started, 1).id();

		// dentro da folga a última resposta ainda entra
		clock.now = start.plus(Duration.ofMinutes(31));
		service.answer("maria", started.id(), first, "B");
		clock.now = start.plus(Duration.ofMinutes(40));

		assertThatThrownBy(() -> service.answer("maria", started.id(), first, "A"))
			.isInstanceOf(BadRequestException.class);
		SimulationView late = service.get("maria", started.id());
		assertThat(late.finished()).isTrue();
		assertThat(late.finishedAt()).isEqualTo(started.deadline());
		assertThat(late.result().correct()).isEqualTo(2);
		// entregar depois do prazo vale o prazo
		assertThat(service.finish("maria", started.id()).finishedAt()).isEqualTo(started.deadline());
	}

	@Test
	void resultadoPorTopicoComAProvaLigada() {
		SimulationView started = service.start("maria", targetId, paperId, null);
		// conteúdo do cargo: "Regência" (questões 1 e 2) e "Atos" (questão 3)
		UUID syllabus = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO extraction (id, task, inputs_hash, prompt_version, inputs, status, created_at, requested_at)
				VALUES (?, 'EDITAL_SYLLABUS', ?, 'v2', '{}', 'DONE', now(), now())""", syllabus, syllabus.toString());
		UUID portugues = UUID.randomUUID();
		UUID direito = UUID.randomUUID();
		jdbc.update("INSERT INTO syllabus_subject (id, extraction_id, ordinal, name) VALUES (?, ?, 0, 'Português'), (?, ?, 1, 'Direito')",
				portugues, syllabus, direito, syllabus);
		UUID regencia = UUID.randomUUID();
		UUID atos = UUID.randomUUID();
		jdbc.update("INSERT INTO syllabus_topic (id, subject_id, ordinal, text) VALUES (?, ?, 0, 'Regência'), (?, ?, 0, 'Atos')",
				regencia, portugues, atos, direito);
		for (var link : List.of(List.of(number(started, 1).id(), regencia), List.of(number(started, 2).id(), regencia),
				List.of(number(started, 3).id(), atos))) {
			jdbc.update("INSERT INTO question_topic (id, question_id, topic_id) VALUES (?, ?, ?)", UUID.randomUUID(),
					link.get(0), link.get(1));
		}
		given(documents.targetSyllabus("maria", targetId))
			.willReturn(Optional.of(new StudyDocumentService.TargetSyllabus(syllabus, null)));
		service.answer("maria", started.id(), number(started, 1).id(), "A");

		// durante a prova o tópico não aparece (seria pista)
		assertThat(number(service.get("maria", started.id()), 1).topic()).isNull();
		SimulationView finished = service.finish("maria", started.id());

		assertThat(number(finished, 1).topic()).isEqualTo(new SimulationService.TopicRef("Português", "Regência"));
		// Regência: 1 errada e 1 em branco, vem primeiro; Atos: anulada (certa)
		assertThat(finished.result().byTopic()).containsExactly(
				new SimulationService.TopicResult(new SimulationService.TopicRef("Português", "Regência"), 2, 0, 1, 1),
				new SimulationService.TopicResult(new SimulationService.TopicRef("Direito", "Atos"), 1, 1, 0, 0));
	}

	@Test
	void praticaSoComAsQuestoesEscolhidasNaOrdem() {
		SimulationView full = service.start("maria", targetId, paperId, null);
		UUID second = number(full, 2).id();
		UUID first = number(full, 1).id();

		SimulationView practice = service.startPractice("maria", targetId, "Prática · Português", List.of(second, first));

		assertThat(practice.title()).isEqualTo("Prática · Português");
		assertThat(practice.deadline()).isNull();
		assertThat(practice.questions()).extracting(QuestionView::number).containsExactly(2, 1);
		// a questão 3 é da prova, mas não desta prática
		assertThatThrownBy(() -> service.answer("maria", practice.id(), number(full, 3).id(), "A"))
			.isInstanceOf(NotFoundException.class);
		service.answer("maria", practice.id(), first, "B");
		assertThat(service.finish("maria", practice.id()).result()).satisfies(r -> {
			assertThat(r.total()).isEqualTo(2);
			assertThat(r.correct()).isEqualTo(1);
		});
		assertThat(service.list("maria", targetId)).extracting(s -> s.total()).contains(2, 3);
	}

	@Test
	void questaoReportadaSaiDaNota() {
		SimulationView started = service.start("maria", targetId, paperId, null);
		service.answer("maria", started.id(), number(started, 1).id(), "B");
		service.answer("maria", started.id(), number(started, 2).id(), "A");
		service.finish("maria", started.id());

		// a 2 (errada) estava com o gabarito errado
		service.report("maria", started.id(), number(started, 2).id(), QuestionReport.Reason.WRONG_ANSWER, " gabarito é A ");

		SimulationView reported = service.get("maria", started.id());
		assertThat(number(reported, 2)).satisfies(q -> {
			assertThat(q.reported()).isTrue();
			assertThat(q.correct()).isNull();
		});
		assertThat(reported.result()).satisfies(r -> {
			assertThat(r.correct()).isEqualTo(2);
			assertThat(r.wrong()).isZero();
			assertThat(r.unscored()).isEqualTo(1);
		});
		assertThat(jdbc.queryForObject("SELECT comment FROM question_report WHERE question_id = ?", String.class,
				number(started, 2).id()))
			.isEqualTo("gabarito é A");
		// reporte de outra pessoa não muda a nota de quem não reportou
		assertThat(number(reported, 1).reported()).isFalse();
		assertThatThrownBy(() -> service.report("maria", started.id(), UUID.randomUUID(), QuestionReport.Reason.OTHER, null))
			.isInstanceOf(NotFoundException.class);
	}

}
