package com.gabarit.core.simulation;

import com.gabarit.core.PostgresTestConfiguration;
import com.gabarit.core.extraction.Extraction;
import com.gabarit.core.extraction.ExtractionFinishedEvent;
import com.gabarit.core.extraction.ExtractionService;
import com.gabarit.core.extraction.ExtractionTask;
import com.gabarit.core.shared.BadRequestException;
import com.gabarit.core.shared.NotFoundException;
import com.gabarit.core.study.EditalOverview;
import com.gabarit.core.study.EditalSyllabus;
import com.gabarit.core.study.StudyDocumentService;
import com.gabarit.core.study.StudyDocumentService.TargetSyllabus;
import com.gabarit.core.study.StudyReadyEvent;
import com.gabarit.core.study.StudyTargetService;
import com.gabarit.core.study.StudyTargetService.StudyTargetDetail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** Postgres real e sem a transação de teste (as cópias rodam em transação própria; ids novos por teste). */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ PostgresTestConfiguration.class, GenerationService.class, TopicService.class, SimulationService.class,
		QuestionCatalog.class, TopicCatalog.class, SimulationServiceTest.Clocks.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@RecordApplicationEvents
class GenerationServiceTest {

	// Português: Crase (0.0), Regência (0.1)
	static final EditalSyllabus SYLLABUS = new EditalSyllabus(true,
			List.of(new EditalSyllabus.Subject("Português", null, List.of("Crase.", "Regência."), 1)));

	static final String GENERATED = """
			{"questions": [
			  {"base_text": "Fui à feira e comprei à vista.", "statement": "Assinale o uso correto da crase no texto.", "options": [{"letter": "A", "text": "à pé"},
			   {"letter": "B", "text": "à vista"}], "answer": "b", "explanation": "À vista é locução feminina."},
			  {"base_text": null, "statement": "Antes de verbo não há crase.", "options": [], "answer": "C", "explanation": "Verbo não admite artigo."}]}
			""";

	@Autowired
	GenerationService service;

	@Autowired
	SimulationService simulations;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	TransactionTemplate transaction;

	@Autowired
	ApplicationEvents events;

	@MockitoBean
	StudyDocumentService documents;

	@MockitoBean
	StudyTargetService targets;

	@MockitoBean
	ExtractionService extractions;

	UUID targetId;

	UUID generation;

	@BeforeEach
	void concursoComConteudo() {
		targetId = UUID.randomUUID();
		generation = UUID.randomUUID();
		UUID syllabusReading = UUID.randomUUID();
		jdbc.update("INSERT INTO study_target (id, user_id, title, created_at, updated_at) VALUES (?, 'maria', 'TJ', now(), now())",
				targetId);
		for (UUID id : List.of(generation, syllabusReading)) {
			jdbc.update("""
					INSERT INTO extraction (id, task, inputs_hash, prompt_version, inputs, status, created_at, requested_at)
					VALUES (?, 'TOPIC_QUESTIONS', ?, 'v1', '{}', 'PENDING', now(), now())""", id, id.toString());
		}
		given(targets.get("maria", targetId)).willReturn(new StudyTargetDetail(targetId, null, "TJ", null, "FGV", null,
				null, null, Instant.parse("2026-11-01T00:00:00Z"), null, List.of()));
		given(documents.targetSyllabus("maria", targetId)).willReturn(Optional.of(new TargetSyllabus(syllabusReading, SYLLABUS)));
		Extraction pending = reading(Extraction.Status.PENDING, null);
		given(extractions.request(any(), eq(ExtractionTask.TOPIC_QUESTIONS), eq(List.of()), anyMap())).willReturn(pending);
		given(extractions.find(anyCollection())).willReturn(Map.of(generation, pending));
	}

	Extraction reading(Extraction.Status status, String result) {
		Extraction extraction = mock(Extraction.class);
		given(extraction.getId()).willReturn(generation);
		given(extraction.getTask()).willReturn(ExtractionTask.TOPIC_QUESTIONS);
		given(extraction.getPromptVersion()).willReturn(ExtractionTask.TOPIC_QUESTIONS.promptVersion());
		given(extraction.getStatus()).willReturn(status);
		given(extraction.getResult()).willReturn(result);
		return extraction;
	}

	void finish() {
		Extraction done = reading(Extraction.Status.DONE, GENERATED);
		given(extractions.find(anyCollection())).willReturn(Map.of(generation, done));
		transaction.executeWithoutResult(status -> service
			.onFinished(new ExtractionFinishedEvent(generation, ExtractionTask.TOPIC_QUESTIONS, Extraction.Status.DONE)));
	}

	@Test
	@SuppressWarnings("unchecked")
	void pedeNoEstiloDaBancaENoFormatoDaProva() {
		var state = service.generate("maria", targetId, 0, 1);

		assertThat(state.status()).isEqualTo(Extraction.Status.PENDING);
		ArgumentCaptor<Map<String, Object>> params = ArgumentCaptor.forClass(Map.class);
		verify(extractions).request(eq("maria"), eq(ExtractionTask.TOPIC_QUESTIONS), eq(List.of()), params.capture());
		assertThat(params.getValue()).containsEntry("board", "FGV")
			.containsEntry("answer_format", "MULTIPLE_CHOICE")
			.containsEntry("options_count", 5)
			.containsEntry("subject", "Português")
			.containsEntry("topic", "Regência.")
			.containsEntry("count", 10)
			// primeira rodada: nada a evitar
			.containsEntry("round", 1L)
			.containsEntry("avoid", List.of());
		assertThatThrownBy(() -> service.generate("maria", targetId, 0, 9)).isInstanceOf(NotFoundException.class);
	}

	@Test
	void geradasEntramNaPraticaDoTopicoComOComentario() {
		service.generate("maria", targetId, 0, 0);
		assertThatThrownBy(() -> service.practice("maria", targetId, 0, 0)).isInstanceOf(BadRequestException.class);

		finish();

		// geração do concurso terminou: aviso no sininho
		assertThat(events.stream(StudyReadyEvent.class)).containsExactly(
				new StudyReadyEvent("maria", targetId, "TJ", StudyReadyEvent.What.QUESTIONS, true));
		assertThat(service.overview("maria", targetId)).first().satisfies(crase -> {
			assertThat(crase.practice()).isEqualTo(2);
			assertThat(crase.unseen()).isEqualTo(2);
			assertThat(crase.generation().status()).isEqualTo(Extraction.Status.DONE);
		});
		var practice = service.practice("maria", targetId, 0, 0);
		assertThat(practice.title()).isEqualTo("Prática · Crase.");
		assertThat(practice.questions()).hasSize(2).allSatisfy(q -> {
			assertThat(q.generated()).isTrue();
			// o comentário só depois de entregar
			assertThat(q.explanation()).isNull();
		});
		// a questão que cita um texto vem com ele
		assertThat(practice.questions()).extracting(SimulationService.QuestionView::baseText)
			.containsExactly("Fui à feira e comprei à vista.", null);
		assertThat(practice.questions().get(1).kind()).isEqualTo("TRUE_FALSE");
		simulations.answer("maria", practice.id(), practice.questions().getFirst().id(), "B");
		var finished = simulations.finish("maria", practice.id());
		assertThat(finished.questions().getFirst()).satisfies(q -> {
			assertThat(q.correct()).isTrue();
			assertThat(q.explanation()).isEqualTo("À vista é locução feminina.");
		});
		assertThat(service.overview("maria", targetId).getFirst().unseen()).isZero();
		// terminado fica; em aberto pode ser apagado, e as questões voltam a ser não vistas
		assertThatThrownBy(() -> simulations.delete("maria", practice.id())).isInstanceOf(BadRequestException.class);
		var again = service.practice("maria", targetId, 0, 0);
		assertThatThrownBy(() -> simulations.delete("joao", again.id())).isInstanceOf(NotFoundException.class);
		simulations.delete("maria", again.id());
		assertThat(simulations.list("maria", targetId)).extracting(SimulationService.SimulationSummary::id)
			.containsExactly(practice.id());
	}

	@Test
	@SuppressWarnings("unchecked")
	void simuladoIneditoNaEstruturaDoQuadroDeProvas() {
		// quadro: 3 questões de Português (Crase recebe 2, Regência 1)
		given(documents.targetExam("maria", targetId)).willReturn(Optional.of(new EditalOverview.Exam(1, "Todos",
				"MULTIPLE_CHOICE", 3, List.of(new EditalOverview.Section(null, "Língua Portuguesa", 3, 1.0)), false)));
		var status = service.mockExam("maria", targetId);
		assertThat(status.questions()).isEqualTo(3);
		assertThat(status.sections()).containsExactly(new GenerationService.MockExamSection("Português", 3));
		assertThat(status.topics()).isEqualTo(2);
		assertThat(status.readyTopics()).isZero();

		assertThat(service.prepareMockExam("maria", targetId).pendingTopics()).isEqualTo(2);
		assertThatThrownBy(() -> service.startMockExam("maria", targetId, 60)).isInstanceOf(BadRequestException.class)
			.hasMessageContaining("preparado");

		finish();

		assertThat(service.mockExam("maria", targetId).readyTopics()).isEqualTo(2);
		var exam = service.startMockExam("maria", targetId, 60);
		assertThat(exam.title()).isEqualTo("Simulado inédito · FGV");
		assertThat(exam.deadline()).isNotNull();
		// as duas geradas (a geração foi a mesma para os dois tópicos)
		assertThat(exam.questions()).hasSize(2).allSatisfy(q -> assertThat(q.generated()).isTrue());

		// já vistas: o próximo simulado pede outra rodada, sem repetir as geradas
		assertThat(service.mockExam("maria", targetId).readyTopics()).isZero();
		service.prepareMockExam("maria", targetId);
		ArgumentCaptor<Map<String, Object>> params = ArgumentCaptor.forClass(Map.class);
		verify(extractions, times(4)).request(eq("maria"), eq(ExtractionTask.TOPIC_QUESTIONS), eq(List.of()),
				params.capture());
		assertThat(params.getAllValues().subList(2, 4)).allSatisfy(p -> assertThat(p).containsEntry("round", 2L)
			.containsEntry("avoid", List.of("Assinale o uso correto da crase no texto.", "Antes de verbo não há crase.")));
	}

}
