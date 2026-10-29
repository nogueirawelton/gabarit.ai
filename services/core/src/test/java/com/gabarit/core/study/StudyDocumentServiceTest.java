package com.gabarit.core.study;

import com.gabarit.core.MinioTestConfiguration;
import com.gabarit.core.PostgresTestConfiguration;
import com.gabarit.core.document.DocumentService;
import com.gabarit.core.exam.CatalogPositionService;
import com.gabarit.core.extraction.Extraction;
import com.gabarit.core.extraction.ExtractionFinishedEvent;
import com.gabarit.core.extraction.ExtractionService;
import com.gabarit.core.extraction.ExtractionTask;
import com.gabarit.core.exam.ExamService;
import com.gabarit.core.shared.BadRequestException;
import com.gabarit.core.shared.ClockConfig;
import com.gabarit.core.shared.NotFoundException;
import com.gabarit.core.shared.ObjectStorage;
import com.gabarit.core.study.StudyDocument.Kind;
import com.gabarit.core.study.StudyDocument.Status;
import com.gabarit.core.user.UserProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import static com.gabarit.core.document.TestPdfs.get;
import static com.gabarit.core.document.TestPdfs.pdf;
import static com.gabarit.core.document.TestPdfs.put;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** Envio ponta a ponta: começar, PUT no MinIO (como o navegador), confirmar. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ PostgresTestConfiguration.class, MinioTestConfiguration.class, DocumentService.class, ObjectStorage.class,
		ClockConfig.class, ExamService.class, StudyTargetService.class, StudyDocumentService.class, EditalReadingListener.class,
		UserProfileService.class, CatalogPositionService.class })
@RecordApplicationEvents
class StudyDocumentServiceTest {

	@Autowired
	EditalReadingListener readingListener;

	@Autowired
	ApplicationEvents events;

	@Autowired
	StudyDocumentService service;

	@Autowired
	StudyTargetService targets;

	@Autowired
	TestEntityManager em;

	// a leitura pela LLM (fila) é do pacote extraction; aqui só importa que seja pedida e vinculada
	@MockitoBean
	ExtractionService extractions;

	static final UUID READING = UUID.fromString("0193a1f0-0000-7000-8000-0000000000aa");

	static final UUID SYLLABUS = UUID.fromString("0193a1f0-0000-7000-8000-0000000000cc");

	static final UUID PAPER_READING = UUID.fromString("0193a1f0-0000-7000-8000-0000000000dd");

	@Autowired
	JdbcTemplate jdbc;

	UUID targetId;

	@BeforeEach
	void concurso() {
		targetId = targets.addCustom("maria", "TJ-SP 2023").id();
		// a linha de verdade (o vínculo tem FK); o resto do pacote extraction fica no mock
		jdbc.update("""
				INSERT INTO extraction (id, task, inputs_hash, prompt_version, inputs, status, created_at, requested_at)
				VALUES (?, 'EDITAL_OVERVIEW', 'teste', 'v1', '{}', 'PENDING', now(), now())
				ON CONFLICT DO NOTHING
				""", READING);
		jdbc.update("""
				INSERT INTO extraction (id, task, inputs_hash, prompt_version, inputs, status, created_at, requested_at)
				VALUES (?, 'EDITAL_SYLLABUS', 'teste', 'v1', '{}', 'PENDING', now(), now())
				ON CONFLICT DO NOTHING
				""", SYLLABUS);
		Extraction pending = extraction(READING, ExtractionTask.EDITAL_OVERVIEW, Extraction.Status.PENDING);
		given(extractions.request(any(), anyList(), anyMap())).willReturn(pending);
		Extraction syllabusPending = extraction(SYLLABUS, ExtractionTask.EDITAL_SYLLABUS, Extraction.Status.PENDING);
		given(extractions.request(eq(ExtractionTask.EDITAL_SYLLABUS), anyList(), anyMap())).willReturn(syllabusPending);
		jdbc.update("""
				INSERT INTO extraction (id, task, inputs_hash, prompt_version, inputs, status, created_at, requested_at)
				VALUES (?, 'EXAM_PAPER', 'teste', 'v1', '{}', 'PENDING', now(), now())
				ON CONFLICT DO NOTHING
				""", PAPER_READING);
		Extraction paperPending = extraction(PAPER_READING, ExtractionTask.EXAM_PAPER, Extraction.Status.PENDING);
		given(extractions.request(eq(ExtractionTask.EXAM_PAPER), anyList(), anyMap())).willReturn(paperPending);
	}

	/** Leitura do pacote extraction (mock), feita com o prompt atual da tarefa. */
	static Extraction extraction(UUID id, ExtractionTask task, Extraction.Status status) {
		Extraction extraction = mock(Extraction.class);
		given(extraction.getId()).willReturn(id);
		given(extraction.getTask()).willReturn(task);
		given(extraction.getPromptVersion()).willReturn(task.promptVersion());
		given(extraction.getStatus()).willReturn(status);
		return extraction;
	}

	StudyDocumentService.DocumentView send(Kind kind, UUID paperId, byte[] content) {
		var started = service.start("maria", targetId, kind, paperId, "arquivo.pdf", content.length);
		assertThat(put(started.uploadUrl(), started.contentType(), content)).isEqualTo(200);
		return service.complete("maria", targetId, started.document().id());
	}

	UUID paper(boolean answersIncluded) {
		return service.addPaper("maria", targetId, 2019, " Tipo 1 ", answersIncluded).id();
	}

	@Test
	void provaAnteriorComProvaEGabaritoNosEspacos() {
		UUID paperId = paper(false);
		send(Kind.PROVA, paperId, pdf(4, "prova"));
		send(Kind.GABARITO, paperId, pdf(1, "gabarito"));
		send(Kind.EDITAL, null, pdf(30, "edital"));

		var materials = service.materials("maria", targetId);

		assertThat(materials.editais()).singleElement().satisfies(e -> assertThat(e.pages()).isEqualTo(30));
		var paper = materials.papers().getFirst();
		assertThat(paper.year()).isEqualTo(2019);
		assertThat(paper.variant()).isEqualTo("Tipo 1");
		assertThat(paper.exam().status()).isEqualTo(Status.READY);
		assertThat(paper.exam().pages()).isEqualTo(4);
		assertThat(get(paper.exam().url()).statusCode()).isEqualTo(200);
		assertThat(paper.answerKey().pages()).isEqualTo(1);
	}

	@Test
	void provaSemArquivosTemEspacosVazios() {
		paper(false);

		var paper = service.materials("maria", targetId).papers().getFirst();

		assertThat(paper.exam()).isNull();
		assertThat(paper.answerKey()).isNull();
	}

	@Test
	void espacoComArquivoAprovadoNaoEhSobrescrito() {
		UUID paperId = paper(false);
		send(Kind.PROVA, paperId, pdf(2, "primeira"));

		assertThatThrownBy(() -> service.start("maria", targetId, Kind.PROVA, paperId, "outra.pdf", 10))
			.isInstanceOf(BadRequestException.class)
			.hasMessageContaining("remova");
	}

	@Test
	void envioRecusadoEhSubstituidoPeloNovo() {
		UUID paperId = paper(false);
		var rejected = send(Kind.PROVA, paperId, "não é pdf".getBytes(StandardCharsets.UTF_8));
		assertThat(rejected.status()).isEqualTo(Status.REJECTED);
		assertThat(rejected.rejectionReason()).contains("não é um PDF");

		send(Kind.PROVA, paperId, pdf(2, "agora-sim"));

		assertThat(service.materials("maria", targetId).papers().getFirst().exam().status()).isEqualTo(Status.READY);
	}

	@Test
	void gabaritoNoMesmoArquivoNaoTemEspacoSeparado() {
		UUID paperId = paper(true);

		assertThatThrownBy(() -> service.start("maria", targetId, Kind.GABARITO, paperId, "gab.pdf", 10))
			.isInstanceOf(BadRequestException.class)
			.hasMessageContaining("mesmo arquivo");
	}

	@Test
	void provaEGabaritoPrecisamDeUmaProvaAnterior() {
		assertThatThrownBy(() -> service.start("maria", targetId, Kind.PROVA, null, "p.pdf", 10))
			.isInstanceOf(BadRequestException.class);
		assertThatThrownBy(() -> service.start("maria", targetId, Kind.GABARITO, null, "g.pdf", 10))
			.isInstanceOf(BadRequestException.class);
	}

	@Test
	void editalDaProvaFicaNaProvaENaoNosEditaisDoConcurso() {
		UUID paperId = paper(false);
		send(Kind.EDITAL, paperId, pdf(12, "edital-2019"));
		send(Kind.EDITAL, null, pdf(20, "edital-atual"));

		var materials = service.materials("maria", targetId);

		assertThat(materials.editais()).singleElement().satisfies(e -> assertThat(e.pages()).isEqualTo(20));
		assertThat(materials.papers().getFirst().edital().pages()).isEqualTo(12);
		assertThatThrownBy(() -> service.start("maria", targetId, Kind.EDITAL, paperId, "outro.pdf", 10))
			.isInstanceOf(BadRequestException.class)
			.hasMessageContaining("o edital");
	}

	@Test
	void mesmoEditalEmDuasProvasEUmArquivoSo() {
		byte[] edital = pdf(5, "edital-compartilhado");
		UUID first = paper(false);
		UUID second = paper(false);

		send(Kind.EDITAL, first, edital);
		send(Kind.EDITAL, second, edital);

		assertThat(jdbc.queryForObject("SELECT count(*) FROM document_blob", Integer.class)).isEqualTo(1);
	}

	@Test
	void confirmarDeNovoDevolveOMesmoResultado() {
		var document = send(Kind.EDITAL, null, pdf(1, "edital"));

		assertThat(service.complete("maria", targetId, document.id()).status()).isEqualTo(Status.READY);
	}

	@Test
	void arquivoGrandeDemaisNemComeca() {
		assertThatThrownBy(() -> service.start("maria", targetId, Kind.EDITAL, null, "enorme.pdf",
				DocumentService.MAX_BYTES + 1))
			.isInstanceOf(BadRequestException.class);
	}

	@Test
	void concursoProvaEArquivoDeOutroUsuario() {
		UUID paperId = paper(false);
		var document = send(Kind.PROVA, paperId, pdf(1, "da-maria"));

		assertThatThrownBy(() -> service.materials("joao", targetId)).isInstanceOf(NotFoundException.class);
		assertThatThrownBy(() -> service.addPaper("joao", targetId, null, null, false))
			.isInstanceOf(NotFoundException.class);
		assertThatThrownBy(() -> service.removePaper("joao", targetId, paperId)).isInstanceOf(NotFoundException.class);
		assertThatThrownBy(() -> service.complete("joao", targetId, document.id()))
			.isInstanceOf(NotFoundException.class);
		assertThatThrownBy(() -> service.remove("joao", targetId, document.id())).isInstanceOf(NotFoundException.class);
	}

	@Test
	void limiteDeProvasPorConcurso() {
		IntStream.range(0, StudyDocumentService.MAX_PAPERS_PER_TARGET).forEach(i -> paper(false));

		assertThatThrownBy(() -> paper(false)).isInstanceOf(BadRequestException.class).hasMessageContaining("remova");
	}

	@Test
	void envioAbandonadoSomeDepoisDeUmDia() {
		var started = service.start("maria", targetId, Kind.EDITAL, null, "edital.pdf", 10);
		em.flush();
		jdbc.update("UPDATE study_document SET created_at = created_at - interval '25 hours' WHERE id = ?",
				started.document().id());
		em.clear();

		service.deleteStaleUploads();

		assertThat(service.materials("maria", targetId).editais()).isEmpty();
	}

	@Test
	void removerAProvaRemoveOsArquivosDela() {
		UUID paperId = paper(false);
		send(Kind.PROVA, paperId, pdf(1, "p"));
		em.flush();

		service.removePaper("maria", targetId, paperId);
		em.flush();

		assertThat(jdbc.queryForObject("SELECT count(*) FROM study_document", Integer.class)).isZero();
	}

	@Test
	void removerOConcursoRemoveTudo() {
		send(Kind.PROVA, paper(false), pdf(1, "cascata"));
		send(Kind.EDITAL, null, pdf(1, "edital"));
		em.flush();

		targets.remove("maria", targetId);
		em.flush();

		assertThat(jdbc.queryForObject("SELECT count(*) FROM study_document", Integer.class)).isZero();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM study_paper", Integer.class)).isZero();
	}

	@Test
	void editalDoConcursoSoELidoQuandoAPessoaPede() {
		byte[] content = pdf(30, "edital");
		var sent = send(Kind.EDITAL, null, content);
		// arquivo errado não gasta leitura: nada é pedido no envio
		assertThat(sent.reading()).isNull();
		verify(extractions, never()).request(any(), anyList(), anyMap());

		var edital = service.read("maria", targetId, sent.id());

		verify(extractions).request(ExtractionTask.EDITAL_OVERVIEW, List.of(sha256(content)), Map.of());
		assertThat(edital.reading().status()).isEqualTo(Extraction.Status.PENDING);
		em.flush();
		assertThat(jdbc.queryForObject("SELECT extraction_id FROM study_document WHERE id = ?", UUID.class, edital.id()))
			.isEqualTo(READING);
	}

	@Test
	void editalDeProvaRelacionadaNaoELido() {
		UUID paperId = paper(false);

		send(Kind.EDITAL, paperId, pdf(10, "edital 2019"));
		var prova = send(Kind.PROVA, paperId, pdf(4, "prova"));

		verify(extractions, never()).request(any(), anyList(), anyMap());
		assertThatThrownBy(() -> service.read("maria", targetId, prova.id())).isInstanceOf(BadRequestException.class);
	}

	@Test
	void lerDeNovoPedeALeituraDoEdital() {
		var edital = lido(pdf(30, "edital"));

		var read = service.read("maria", targetId, edital.id());

		assertThat(read.reading().status()).isEqualTo(Extraction.Status.PENDING);
		verify(extractions, times(2)).request(any(), anyList(), anyMap());
	}

	/** Edital do concurso enviado e com a leitura pedida. */
	StudyDocumentService.DocumentView lido(byte[] content) {
		return service.read("maria", targetId, send(Kind.EDITAL, null, content).id());
	}

	static String sha256(byte[] content) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	@Test
	void leituraProntaAvisaOConcursoQueTemOEdital() {
		lido(pdf(30, "edital"));
		// concurso de outra pessoa, sem esse edital: não é avisado
		targets.addCustom("joao", "TJ-SP 2023 (João)");
		em.flush();

		readingListener
			.onExtractionFinished(new ExtractionFinishedEvent(READING, ExtractionTask.EDITAL_OVERVIEW, Extraction.Status.DONE));

		assertThat(events.stream(EditalReadEvent.class))
			.containsExactly(new EditalReadEvent("maria", targetId, "TJ-SP 2023", true));
	}

	static final String OVERVIEW = """
			{"organ": "TJ-SP", "board": "Vunesp", "year": 2026, "registration_start": null,
			 "registration_end": null, "exam_date": null, "positions": [
			  {"name": "Analista Judiciário", "specialty": "Direito", "education_level": "SUPERIOR",
			   "openings": "3", "salary": null, "page": 4},
			  {"name": "Escrevente", "specialty": null, "education_level": "MEDIO",
			   "openings": "10", "salary": null, "page": 5}]}
			""";

	/** A leitura READING pronta, com a visão geral acima. */
	void leituraPronta() {
		Extraction done = extraction(READING, ExtractionTask.EDITAL_OVERVIEW, Extraction.Status.DONE);
		given(done.getResult()).willReturn(OVERVIEW);
		given(extractions.find(anyCollection())).willReturn(Map.of(READING, done));
	}

	@Test
	void cargoEscolhidoFicaNoEditalEOConteudoSoELidoQuandoAPessoaPede() {
		byte[] content = pdf(30, "edital");
		var edital = lido(content);
		leituraPronta();

		var chosen = service.choosePosition("maria", targetId, edital.id(), "Analista Judiciário", "Direito");

		assertThat(chosen.position()).isEqualTo(new StudyDocumentService.Position("Analista Judiciário", "Direito"));
		// escolher só grava: cargo errado não gasta leitura
		assertThat(chosen.syllabus()).isNull();
		verify(extractions, never()).request(eq(ExtractionTask.EDITAL_SYLLABUS), anyList(), anyMap());
		// concurso à mão: vira também o cargo do concurso
		assertThat(targets.get("maria", targetId).positionName()).isEqualTo("Analista Judiciário – Direito");

		var read = service.readSyllabus("maria", targetId, edital.id());

		assertThat(read.syllabus().status()).isEqualTo(Extraction.Status.PENDING);
		verify(extractions).request(ExtractionTask.EDITAL_SYLLABUS, List.of(sha256(content)),
				Map.of("position", Map.of("name", "Analista Judiciário", "specialty", "Direito")));
		em.flush();
		assertThat(jdbc.queryForObject("SELECT syllabus_extraction_id FROM study_document WHERE id = ?", UUID.class,
				edital.id()))
			.isEqualTo(SYLLABUS);

		// outro cargo desfaz o vínculo com o conteúdo do anterior
		var other = service.choosePosition("maria", targetId, edital.id(), "Escrevente", null);
		assertThat(other.position().specialty()).isNull();
		assertThat(other.syllabus()).isNull();
		// sem especialidade, o cargo sem áreas (vai nula no pedido)
		service.readSyllabus("maria", targetId, edital.id());
		Map<String, Object> escrevente = new HashMap<>();
		escrevente.put("name", "Escrevente");
		escrevente.put("specialty", null);
		verify(extractions).request(ExtractionTask.EDITAL_SYLLABUS, List.of(sha256(content)),
				Map.of("position", escrevente));
	}

	@Test
	void cargoForaDaLeituraOuLeituraNaoProntaNaoEEscolhido() {
		var edital = lido(pdf(30, "edital"));

		// leitura ainda pendente
		assertThatThrownBy(() -> service.choosePosition("maria", targetId, edital.id(), "Escrevente", null))
			.isInstanceOf(BadRequestException.class);
		leituraPronta();
		// a especialidade faz parte do cargo
		assertThatThrownBy(() -> service.choosePosition("maria", targetId, edital.id(), "Analista Judiciário", null))
			.isInstanceOf(BadRequestException.class)
			.hasMessageContaining("Analista Judiciário");
		assertThatThrownBy(() -> service.readSyllabus("maria", targetId, edital.id()))
			.as("conteúdo sem cargo escolhido")
			.isInstanceOf(BadRequestException.class);
		assertThatThrownBy(() -> service.choosePosition("joao", targetId, edital.id(), "Escrevente", null))
			.isInstanceOf(NotFoundException.class);
	}

	@Test
	void outraLeituraDoEditalMantemOCargoEOConteudo() {
		var edital = lido(pdf(30, "edital"));
		leituraPronta();
		service.choosePosition("maria", targetId, edital.id(), "Escrevente", null);
		service.readSyllabus("maria", targetId, edital.id());
		// prompt novo: outra leitura do mesmo PDF
		UUID nova = UUID.fromString("0193a1f0-0000-7000-8000-0000000000bb");
		jdbc.update("""
				INSERT INTO extraction (id, task, inputs_hash, prompt_version, inputs, status, created_at, requested_at)
				VALUES (?, 'EDITAL_OVERVIEW', 'teste', 'v2', '{}', 'PENDING', now(), now())
				""", nova);
		Extraction pending = extraction(nova, ExtractionTask.EDITAL_OVERVIEW, Extraction.Status.PENDING);
		given(extractions.request(any(), anyList(), anyMap())).willReturn(pending);

		var read = service.read("maria", targetId, edital.id());

		// os cargos são os mesmos na prática; o conteúdo é pedido pelo nome do cargo
		assertThat(read.position()).isEqualTo(new StudyDocumentService.Position("Escrevente", null));
		em.flush();
		assertThat(jdbc.queryForObject("SELECT extraction_id FROM study_document WHERE id = ?", UUID.class, edital.id()))
			.isEqualTo(nova);
		assertThat(jdbc.queryForObject("SELECT syllabus_extraction_id FROM study_document WHERE id = ?", UUID.class,
				edital.id()))
			.isEqualTo(SYLLABUS);
	}

	@Test
	void leituraDeUmPromptAntigoEstaDesatualizada() {
		var edital = lido(pdf(30, "edital"));
		Extraction old = extraction(READING, ExtractionTask.EDITAL_OVERVIEW, Extraction.Status.DONE);
		given(old.getPromptVersion()).willReturn("v1");
		given(old.getResult()).willReturn(OVERVIEW);
		given(extractions.find(anyCollection())).willReturn(Map.of(READING, old));

		var materials = service.materials("maria", targetId);

		assertThat(materials.editais()).singleElement().satisfies(d -> {
			assertThat(d.id()).isEqualTo(edital.id());
			assertThat(d.reading().outdated()).isTrue();
		});
	}

	@Test
	void provaSoELidaComAProvaEOGabarito() {
		UUID paperId = paper(false);
		assertThatThrownBy(() -> service.readPaper("maria", targetId, paperId)).isInstanceOf(BadRequestException.class);
		byte[] exam = pdf(4, "prova");
		byte[] answerKey = pdf(1, "gabarito");
		send(Kind.PROVA, paperId, exam);
		// só a prova não basta: sem gabarito não há correção
		assertThatThrownBy(() -> service.readPaper("maria", targetId, paperId)).isInstanceOf(BadRequestException.class);
		send(Kind.GABARITO, paperId, answerKey);

		var read = service.readPaper("maria", targetId, paperId);

		assertThat(read.reading().status()).isEqualTo(Extraction.Status.PENDING);
		verify(extractions).request(ExtractionTask.EXAM_PAPER, List.of(sha256(exam), sha256(answerKey)),
				Map.of("variant", "Tipo 1"));
	}

	@Test
	void provaComGabaritoNoMesmoArquivoMandaSoAProva() {
		UUID paperId = paper(true);
		byte[] exam = pdf(4, "prova com gabarito");
		send(Kind.PROVA, paperId, exam);

		service.readPaper("maria", targetId, paperId);

		verify(extractions).request(ExtractionTask.EXAM_PAPER, List.of(sha256(exam)), Map.of("variant", "Tipo 1"));
	}

	@Test
	void trocarOPdfDaProvaDesfazALeitura() {
		UUID paperId = paper(false);
		var exam = send(Kind.PROVA, paperId, pdf(4, "prova"));
		send(Kind.GABARITO, paperId, pdf(1, "gabarito"));
		service.readPaper("maria", targetId, paperId);

		service.remove("maria", targetId, exam.id());

		em.flush();
		assertThat(jdbc.queryForObject("SELECT extraction_id FROM study_paper WHERE id = ?", UUID.class, paperId)).isNull();
	}

}
