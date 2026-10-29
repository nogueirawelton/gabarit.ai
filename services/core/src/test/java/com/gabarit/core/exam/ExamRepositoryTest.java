package com.gabarit.core.exam;

import com.gabarit.core.PostgresTestConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Banco de verdade (Testcontainers) com as migrations do Flyway; o ddl-auto=validate confere as entidades
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresTestConfiguration.class)
class ExamRepositoryTest {

	static final Instant T1 = Instant.parse("2026-10-27T09:00:00Z");

	@Autowired
	ExamRepository repository;

	@Autowired
	TestEntityManager em;

	@Autowired
	JdbcTemplate jdbc;

	static ExamSnapshot snapshot(String externalId, String... positions) {
		return new ExamSnapshot("pciconcursos", externalId, "Concurso " + externalId,
				"https://example.com/" + externalId, null, "NACIONAL", null, null, LocalDate.of(2026, 10, 28),
				LocalDate.of(2026, 12, 10), null, Arrays.stream(positions).map(ExamSnapshot.Position::new).toList());
	}

	@Test
	void salvaEBuscaPelaIdentidadeComOsCargos() {
		repository.save(Exam.create(snapshot("1", "ANALISTA", "TÉCNICO"), T1));
		em.flush();
		em.clear();

		Exam found = repository.findBySourceAndExternalId("pciconcursos", "1").orElseThrow();

		assertThat(found.getId()).isNotNull();
		assertThat(found.getCreatedAt()).isNotNull();
		assertThat(found.getSourceUpdatedAt()).isEqualTo(T1);
		assertThat(found.getPositions()).extracting(ExamPosition::getName).containsExactly("ANALISTA", "TÉCNICO");
		assertThat(repository.findBySourceAndExternalId("pciconcursos", "2")).isEmpty();
	}

	@Test
	void cargoInativoContinuaNoBanco() {
		Exam exam = repository.save(Exam.create(snapshot("1", "ANALISTA", "TÉCNICO"), T1));
		em.flush();

		exam.apply(snapshot("1", "ANALISTA"), T1.plusSeconds(60));
		em.flush();

		List<Boolean> active = jdbc.queryForList(
				"SELECT active FROM exam_position WHERE exam_id = ? ORDER BY name", Boolean.class, exam.getId());
		assertThat(active).containsExactly(true, false);
	}

	@Test
	void slugsDoCatalogoSaoGravadosComoArray() {
		ExamSnapshot snapshot = new ExamSnapshot("pciconcursos", "9", "Concurso 9", "https://example.com/9", null,
				null, null, null, null, null, null,
				List.of(new ExamSnapshot.Position("ENFERMEIRO PSF", List.of("enfermeiro", "enfermeiro-psf"))));
		repository.save(Exam.create(snapshot, T1));
		em.flush();
		em.clear();

		Exam found = repository.findBySourceAndExternalId("pciconcursos", "9").orElseThrow();
		assertThat(found.getPositions().getFirst().getCatalogSlugs()).containsExactly("enfermeiro", "enfermeiro-psf");
		// consulta pelo índice GIN: concursos com cargo no catálogo "enfermeiro-psf"
		Integer count = jdbc.queryForObject(
				"SELECT count(*) FROM exam_position WHERE catalog_slugs @> ARRAY['enfermeiro-psf']::VARCHAR[]", Integer.class);
		assertThat(count).isEqualTo(1);
	}

	@Test
	void mesmaIdentidadeDuasVezesViolaAUnique() {
		repository.save(Exam.create(snapshot("1"), T1));
		em.flush();

		// com IDENTITY o INSERT sai no save (o Hibernate precisa do id), então o erro já aparece aqui
		assertThatThrownBy(() -> repository.saveAndFlush(Exam.create(snapshot("1"), T1)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

}
