package com.gabarit.core.study;

import com.gabarit.core.PostgresTestConfiguration;
import com.gabarit.core.exam.CatalogPositionService;
import com.gabarit.core.exam.ExamFilter;
import com.gabarit.core.exam.ExamService;
import com.gabarit.core.exam.ExamSnapshot;
import com.gabarit.core.shared.BadRequestException;
import com.gabarit.core.shared.ClockConfig;
import com.gabarit.core.shared.NotFoundException;
import com.gabarit.core.study.StudyTargetService.Details;
import com.gabarit.core.user.UserProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ PostgresTestConfiguration.class, ExamService.class, StudyTargetService.class, UserProfileService.class,
		CatalogPositionService.class, StudyTargetServiceTest.FixedClock.class })
class StudyTargetServiceTest {

	@TestConfiguration(proxyBeanMethods = false)
	static class FixedClock {

		@Bean
		Clock clock() {
			return Clock.fixed(ZonedDateTime.of(LocalDate.of(2026, 11, 10).atTime(12, 0), ClockConfig.ZONE).toInstant(),
					ClockConfig.ZONE);
		}

	}

	@Autowired
	StudyTargetService service;

	@Autowired
	ExamService exams;

	@Autowired
	TestEntityManager em;

	@Autowired
	CatalogPositionService catalog;

	@Autowired
	UserProfileService profiles;

	/** Perfil seguindo esses cargos do catálogo. */
	void follows(String userId, String... slugs) {
		catalog.sync("pciconcursos", List.of(new CatalogPositionService.Entry("enfermeiro", "Enfermeiro"),
				new CatalogPositionService.Entry("analista", "Analista")), Instant.parse("2026-11-01T09:00:00Z"));
		var ids = catalog.list("pciconcursos")
			.stream()
			.filter(p -> List.of(slugs).contains(p.getSlug()))
			.map(p -> p.getId())
			.toList();
		profiles.update(userId, ids, List.of(), List.of());
	}

	UUID examId;

	@BeforeEach
	void concurso() {
		exams.upsert(new ExamSnapshot("pciconcursos", "1", "Prefeitura de Santos - SP", "https://example.com/1", "SP",
				null, "Superior", null, null, LocalDate.of(2026, 12, 10), null,
				List.of(new ExamSnapshot.Position("Enfermeiro", List.of("enfermeiro")),
						new ExamSnapshot.Position("Analista", List.of("analista")))),
				Instant.parse("2026-11-01T09:00:00Z"));
		examId = exams.search(ExamFilter.all(), 0, 1).getContent().getFirst().id();
		em.flush();
		em.clear();
	}

	@Test
	void concursoDaListaEntraComTituloOrgaoEAno() {
		var added = service.addExam("maria", examId);

		assertThat(added.created()).isTrue();
		assertThat(added.target().title()).isEqualTo("Prefeitura de Santos - SP");
		assertThat(added.target().organ()).isEqualTo("Prefeitura de Santos");
		assertThat(added.target().year()).isEqualTo(2026);
		assertThat(added.target().exam().positions()).extracting(p -> p.name()).containsExactly("Analista", "Enfermeiro");
	}

	@Test
	void cargoDoPerfilJaVemEscolhido() {
		follows("maria", "enfermeiro");

		var target = service.addExam("maria", examId).target();

		assertThat(target.positionName()).isEqualTo("Enfermeiro");
		assertThat(target.suggestedPositions()).containsExactly("Enfermeiro");
	}

	@Test
	void doisCargosDoPerfilNaoEscolhePorElaMasSugere() {
		follows("maria", "enfermeiro", "analista");

		var target = service.addExam("maria", examId).target();

		assertThat(target.positionName()).isNull();
		assertThat(target.suggestedPositions()).containsExactly("Analista", "Enfermeiro");
	}

	@Test
	void semCargoDoPerfilNaoSugereNada() {
		var target = service.addExam("maria", examId).target();

		assertThat(target.positionName()).isNull();
		assertThat(target.suggestedPositions()).isEmpty();
	}

	@Test
	void adicionarDeNovoNaoDuplica() {
		UUID first = service.addExam("maria", examId).target().id();

		var again = service.addExam("maria", examId);

		assertThat(again.created()).isFalse();
		assertThat(again.target().id()).isEqualTo(first);
		assertThat(service.list("maria")).hasSize(1);
	}

	@Test
	void concursoQueNaoExisteE404() {
		assertThatThrownBy(() -> service.addExam("maria", UUID.randomUUID())).isInstanceOf(NotFoundException.class);
	}

	@Test
	void listaMaisRecentesPrimeiroComOConcursoDaLista() {
		service.addExam("maria", examId);
		service.addCustom("maria", "  TJ-SP 2023 - Escrevente  ");

		var list = service.list("maria");

		assertThat(list).extracting(StudyTargetService.StudyTargetSummary::title)
			.containsExactly("TJ-SP 2023 - Escrevente", "Prefeitura de Santos - SP");
		assertThat(list.getFirst().exam()).isNull();
		assertThat(list.getLast().exam().id()).isEqualTo(examId);
		assertThat(service.list("joao")).isEmpty();
	}

	@Test
	void cargoDeConcursoDaListaTemQueSerDoConcurso() {
		UUID id = service.addExam("maria", examId).target().id();

		var updated = service.update("maria", id,
				new Details("Santos", " Prefeitura ", "", 2026, "Enfermeiro", LocalDate.of(2027, 1, 20)));

		assertThat(updated.positionName()).isEqualTo("Enfermeiro");
		assertThat(updated.organ()).isEqualTo("Prefeitura");
		assertThat(updated.board()).as("vazio apaga").isNull();
		assertThatThrownBy(() -> service.update("maria", id, new Details("Santos", null, null, null, "Médico", null)))
			.isInstanceOf(BadRequestException.class)
			.hasMessageContaining("Médico");
	}

	@Test
	void concursoAMaoAceitaQualquerCargo() {
		UUID id = service.addCustom("maria", "TJ-SP 2023").id();

		var updated = service.update("maria", id,
				new Details("TJ-SP 2023", "TJ-SP", "Vunesp", 2023, "Escrevente Técnico Judiciário", null));

		assertThat(updated.board()).isEqualTo("Vunesp");
		assertThat(updated.positionName()).isEqualTo("Escrevente Técnico Judiciário");
	}

	@Test
	void naoMexeNaCarteiraDeOutroUsuario() {
		UUID id = service.addCustom("maria", "TJ-SP 2023").id();

		assertThatThrownBy(() -> service.get("joao", id)).isInstanceOf(NotFoundException.class);
		assertThatThrownBy(() -> service.update("joao", id, new Details("x", null, null, null, null, null)))
			.isInstanceOf(NotFoundException.class);
		assertThatThrownBy(() -> service.remove("joao", id)).isInstanceOf(NotFoundException.class);
	}

	@Test
	void remove() {
		UUID id = service.addCustom("maria", "TJ-SP 2023").id();

		service.remove("maria", id);

		assertThat(service.list("maria")).isEmpty();
	}

	@Test
	void carteiraTemLimite() {
		IntStream.range(0, StudyTargetService.MAX_TARGETS).forEach(i -> service.addCustom("maria", "Concurso " + i));

		assertThatThrownBy(() -> service.addCustom("maria", "Mais um")).isInstanceOf(BadRequestException.class);
		assertThatThrownBy(() -> service.addExam("maria", examId)).isInstanceOf(BadRequestException.class);
	}

}
