package com.gabarit.core.simulation;

import com.gabarit.core.PostgresTestConfiguration;
import com.gabarit.core.extraction.Extraction;
import com.gabarit.core.extraction.ExtractionFinishedEvent;
import com.gabarit.core.extraction.ExtractionService;
import com.gabarit.core.extraction.ExtractionTask;
import com.gabarit.core.shared.BadRequestException;
import com.gabarit.core.simulation.TopicService.TopicFrequency;
import com.gabarit.core.study.EditalSyllabus;
import com.gabarit.core.study.ExamPaper;
import com.gabarit.core.study.StudyDocumentService;
import com.gabarit.core.study.StudyDocumentService.PaperQuestions;
import com.gabarit.core.study.StudyDocumentService.TargetSyllabus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** Postgres real e sem a transação de teste (as cópias rodam em transação própria; ids novos por teste). */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ PostgresTestConfiguration.class, TopicService.class, QuestionCatalog.class, TopicCatalog.class,
		SimulationServiceTest.Clocks.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TopicServiceTest {

	static final JsonMapper SNAKE = JsonMapper.builder()
		.propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
		.build();

	// tópicos 1.1, 1.2 (Português) e 2.1 (Direito)
	static final String SYLLABUS = """
			{"position_found": true, "subjects": [
			  {"name": "Português", "group": null, "topics": ["Interpretação de textos.", "Crase."], "page": 1},
			  {"name": "Direito", "group": null, "topics": ["Atos administrativos."], "page": 2}]}
			""";

	@Autowired
	TopicService service;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	TransactionTemplate transaction;

	@MockitoBean
	StudyDocumentService documents;

	@MockitoBean
	ExtractionService extractions;

	UUID targetId;

	UUID paperId;

	UUID examReading;

	UUID syllabusReading;

	UUID classification;

	@BeforeEach
	void provaEConteudoLidos() {
		targetId = UUID.randomUUID();
		paperId = UUID.randomUUID();
		examReading = extraction("EXAM_PAPER");
		syllabusReading = extraction("EDITAL_SYLLABUS");
		classification = extraction("QUESTION_TOPICS");
		jdbc.update("INSERT INTO study_target (id, user_id, title, created_at, updated_at) VALUES (?, 'maria', 'TJ', now(), now())",
				targetId);
		jdbc.update("""
				INSERT INTO study_paper (id, user_id, study_target_id, answers_included, created_at, extraction_id)
				VALUES (?, 'maria', ?, true, now(), ?)""", paperId, targetId, examReading);
		ExamPaper paper = SNAKE.readValue(SimulationServiceTest.PAPER, ExamPaper.class);
		given(documents.paperQuestions("maria", targetId, paperId))
			.willReturn(new PaperQuestions(examReading, "Prova de 2019", paper));
		given(documents.targetSyllabus("maria", targetId)).willReturn(
				Optional.of(new TargetSyllabus(syllabusReading, SNAKE.readValue(SYLLABUS, EditalSyllabus.class))));
		given(documents.paperReadings("maria", targetId)).willReturn(Map.of(paperId, examReading));
		Extraction pending = reading(classification, Extraction.Status.PENDING, null);
		given(extractions.request(any(), eq(ExtractionTask.QUESTION_TOPICS), anyList(), anyMap()))
			.willReturn(pending);
		given(extractions.find(anyCollection())).willReturn(Map.of(classification, pending));
	}

	UUID extraction(String task) {
		UUID id = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO extraction (id, task, inputs_hash, prompt_version, inputs, status, created_at, requested_at)
				VALUES (?, ?, ?, 'v1', '{}', 'DONE', now(), now())""", id, task, id.toString());
		return id;
	}

	static Extraction reading(UUID id, Extraction.Status status, String result) {
		Extraction extraction = mock(Extraction.class);
		given(extraction.getId()).willReturn(id);
		given(extraction.getTask()).willReturn(ExtractionTask.QUESTION_TOPICS);
		given(extraction.getPromptVersion()).willReturn(ExtractionTask.QUESTION_TOPICS.promptVersion());
		given(extraction.getStatus()).willReturn(status);
		given(extraction.getResult()).willReturn(result);
		return extraction;
	}

	/** A leitura QUESTION_TOPICS termina com este resultado (como o ExtractionService avisa). */
	void finish(String result) {
		Extraction done = reading(classification, Extraction.Status.DONE, result);
		given(extractions.find(anyCollection())).willReturn(Map.of(classification, done));
		transaction.executeWithoutResult(status -> service
			.onFinished(new ExtractionFinishedEvent(classification, ExtractionTask.QUESTION_TOPICS, Extraction.Status.DONE)));
	}

	@Test
	@SuppressWarnings("unchecked")
	void pedeComAsQuestoesEOsTopicosPorCodigo() {
		var requested = service.classify("maria", targetId, paperId);

		assertThat(requested.status()).isEqualTo(Extraction.Status.PENDING);
		ArgumentCaptor<Map<String, Object>> params = ArgumentCaptor.forClass(Map.class);
		verify(extractions).request(eq("maria"), eq(ExtractionTask.QUESTION_TOPICS), eq(List.of()), params.capture());
		assertThat(params.getValue()).containsEntry("exam_reading_id", examReading.toString())
			.containsEntry("syllabus_reading_id", syllabusReading.toString());
		assertThat((List<Map<String, Object>>) params.getValue().get("topics")).extracting(t -> t.get("code"))
			.containsExactly("1.1", "1.2", "2.1");
		// as quatro questões, com as alternativas como texto
		assertThat((List<Map<String, Object>>) params.getValue().get("questions")).hasSize(4)
			.first()
			.satisfies(q -> assertThat((List<String>) q.get("options")).first().isEqualTo("A) a"));
	}

	@Test
	void leituraProntaMostraOQueMaisCai() {
		service.classify("maria", targetId, paperId);

		// 0 e 1 em Crase; código inventado e null ficam de fora
		finish("""
				{"classifications": [{"ordinal": 0, "topic": "1.2"}, {"ordinal": 1, "topic": "1.2"},
				 {"ordinal": 2, "topic": "9.9"}, {"ordinal": 3, "topic": null}]}""");

		var overview = service.overview("maria", targetId);
		assertThat(overview.syllabusReady()).isTrue();
		assertThat(overview.papers()).singleElement().satisfies(p -> {
			assertThat(p.status()).isEqualTo(Extraction.Status.DONE);
			assertThat(p.outdated()).isFalse();
		});
		assertThat(overview.frequency()).containsExactly(new TopicFrequency(0, 1, 2));

		// outra leitura (prompt novo) troca as ligações em vez de somar
		finish("""
				{"classifications": [{"ordinal": 0, "topic": "2.1"}]}""");
		assertThat(service.overview("maria", targetId).frequency()).containsExactly(new TopicFrequency(1, 0, 1));
	}

	@Test
	void provaRelidaDeixaALigacaoDesatualizada() {
		service.classify("maria", targetId, paperId);
		finish("""
				{"classifications": [{"ordinal": 0, "topic": "1.1"}]}""");
		given(documents.paperReadings("maria", targetId)).willReturn(Map.of(paperId, UUID.randomUUID()));

		var overview = service.overview("maria", targetId);

		assertThat(overview.papers()).singleElement().satisfies(p -> assertThat(p.outdated()).isTrue());
		// a contagem só usa a leitura atual da prova
		assertThat(overview.frequency()).isEmpty();
	}

	@Test
	void semConteudoDoCargoNaoLiga() {
		given(documents.targetSyllabus("maria", targetId)).willReturn(Optional.empty());

		assertThatThrownBy(() -> service.classify("maria", targetId, paperId)).isInstanceOf(BadRequestException.class)
			.hasMessageContaining("conteúdo programático");
		assertThat(service.overview("maria", targetId).syllabusReady()).isFalse();
	}

	@Test
	void questoesParaPraticarPorTopicoSemAnuladaNemDiscursiva() {
		service.classify("maria", targetId, paperId);
		// 0 e 1 em Crase; 2 (anulada) em Atos; 3 (discursiva) em Interpretação
		finish("""
				{"classifications": [{"ordinal": 0, "topic": "1.2"}, {"ordinal": 1, "topic": "1.2"},
				 {"ordinal": 2, "topic": "2.1"}, {"ordinal": 3, "topic": "1.1"}]}""");

		var practice = service.practiceQuestions("maria", targetId);

		assertThat(practice).containsOnlyKeys(new SimulationService.TopicRef("Português", "Crase."));
		assertThat(practice.get(new SimulationService.TopicRef("Português", "Crase."))).hasSize(2);
	}

	@Test
	void questaoReportadaSaiDaPratica() {
		service.classify("maria", targetId, paperId);
		finish("""
				{"classifications": [{"ordinal": 0, "topic": "1.2"}, {"ordinal": 1, "topic": "1.2"}]}""");
		UUID first = jdbc.queryForObject("SELECT id FROM question WHERE extraction_id = ? AND ordinal = 0", UUID.class,
				examReading);
		jdbc.update("""
				INSERT INTO question_report (id, question_id, user_id, reason, created_at)
				VALUES (?, ?, 'maria', 'WRONG_ANSWER', now())""", UUID.randomUUID(), first);

		var practice = service.practiceQuestions("maria", targetId);

		assertThat(practice.get(new SimulationService.TopicRef("Português", "Crase."))).hasSize(1).doesNotContain(first);
	}

}
