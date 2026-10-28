package com.gabarit.core.exam;

import com.gabarit.core.PostgresTestConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ PostgresTestConfiguration.class, CatalogPositionService.class })
class CatalogPositionServiceTest {

	static final Instant T1 = Instant.parse("2026-10-27T09:00:00Z");

	static final Instant T2 = Instant.parse("2026-10-27T21:00:00Z");

	@Autowired
	CatalogPositionService service;

	@Autowired
	CatalogPositionRepository repository;

	static CatalogPositionService.Entry entry(String slug, String name) {
		return new CatalogPositionService.Entry(slug, name);
	}

	@Test
	void criaAtualizaEIgnoraEventoAntigo() {
		var first = service.sync("pciconcursos", List.of(entry("enfermeiro", "ENFERMEIRO"), entry("medico", "Médico")),
				T1);
		assertThat(first).isEqualTo(new CatalogPositionService.SyncResult(2, 0, 0));

		var second = service.sync("pciconcursos", List.of(entry("enfermeiro", "Enfermeiro")), T2);
		assertThat(second).isEqualTo(new CatalogPositionService.SyncResult(0, 1, 0));

		// atrasado: não volta o nome antigo
		var late = service.sync("pciconcursos", List.of(entry("enfermeiro", "ENFERMEIRO")), T1);
		assertThat(late).isEqualTo(new CatalogPositionService.SyncResult(0, 0, 1));

		assertThat(repository.findBySource("pciconcursos")).extracting(CatalogPosition::getSlug, CatalogPosition::getName)
			// "medico" não veio no segundo evento e continua lá
			.containsExactlyInAnyOrder(tuple("enfermeiro", "Enfermeiro"),
					tuple("medico", "Médico"));
	}

	@Test
	void fontesDiferentesNaoSeMisturam() {
		service.sync("pciconcursos", List.of(entry("enfermeiro", "Enfermeiro")), T1);
		service.sync("outra", List.of(entry("enfermeiro", "Enfermeiro(a)")), T1);

		assertThat(repository.findBySource("outra")).extracting(CatalogPosition::getName).containsExactly("Enfermeiro(a)");
	}

}
