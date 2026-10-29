package com.gabarit.core.study;

import com.gabarit.core.MinioTestConfiguration;
import com.gabarit.core.PostgresTestConfiguration;
import com.gabarit.core.document.DocumentService;
import com.gabarit.core.exam.CatalogPositionService;
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

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.stream.IntStream;

import static com.gabarit.core.document.TestPdfs.get;
import static com.gabarit.core.document.TestPdfs.pdf;
import static com.gabarit.core.document.TestPdfs.put;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Envio ponta a ponta: começar, PUT no MinIO (como o navegador), confirmar. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ PostgresTestConfiguration.class, MinioTestConfiguration.class, DocumentService.class, ObjectStorage.class,
		ClockConfig.class, ExamService.class, StudyTargetService.class, StudyDocumentService.class,
		UserProfileService.class, CatalogPositionService.class })
class StudyDocumentServiceTest {

	@Autowired
	StudyDocumentService service;

	@Autowired
	StudyTargetService targets;

	@Autowired
	TestEntityManager em;

	@Autowired
	JdbcTemplate jdbc;

	UUID targetId;

	@BeforeEach
	void concurso() {
		targetId = targets.addCustom("maria", "TJ-SP 2023").id();
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

}
