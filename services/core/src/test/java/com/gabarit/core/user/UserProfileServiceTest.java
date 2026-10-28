package com.gabarit.core.user;

import com.gabarit.core.PostgresTestConfiguration;
import com.gabarit.core.exam.CatalogPosition;
import com.gabarit.core.exam.CatalogPositionService;
import com.gabarit.core.shared.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ PostgresTestConfiguration.class, UserProfileService.class, CatalogPositionService.class })
class UserProfileServiceTest {

	@Autowired
	UserProfileService service;

	@Autowired
	CatalogPositionService catalog;

	Map<String, Long> ids;

	@BeforeEach
	void catalogo() {
		catalog.sync("pciconcursos", List.of(new CatalogPositionService.Entry("enfermeiro", "Enfermeiro"),
				new CatalogPositionService.Entry("agente-de-endemias", "AGENTE DE ENDEMIAS"),
				new CatalogPositionService.Entry("analista", "Analista")), Instant.parse("2026-10-28T09:00:00Z"));
		ids = catalog.list("pciconcursos").stream()
			.collect(Collectors.toMap(CatalogPosition::getSlug, CatalogPosition::getId));
	}

	@Test
	void quemNuncaSalvouRecebePerfilVazio() {
		var profile = service.get("kc-novo");

		assertThat(profile.positions()).isEmpty();
		assertThat(profile.ufs()).isEmpty();
	}

	@Test
	void salvaELeOPerfilComCargosEmOrdemAlfabetica() {
		service.update("kc-1", List.of(ids.get("enfermeiro"), ids.get("agente-de-endemias")), List.of("sp", "MG"));

		var profile = service.get("kc-1");

		// ordem alfabética do português, ignorando caixa
		assertThat(profile.positions()).extracting(CatalogPosition::getSlug)
			.containsExactly("agente-de-endemias", "enfermeiro");
		assertThat(profile.ufs()).containsExactly("MG", "SP");
	}

	@Test
	void salvarDeNovoSubstituiTudo() {
		service.update("kc-2", List.of(ids.get("enfermeiro")), List.of("SP"));

		service.update("kc-2", List.of(ids.get("analista")), List.of());

		var profile = service.get("kc-2");
		assertThat(profile.positions()).extracting(CatalogPosition::getSlug).containsExactly("analista");
		assertThat(profile.ufs()).isEmpty();
	}

	@Test
	void recusaCargoQueNaoExiste() {
		assertThatThrownBy(() -> service.update("kc-3", List.of(ids.get("enfermeiro"), 999_999L), List.of()))
			.isInstanceOf(BadRequestException.class)
			.hasMessageContaining("999999");
	}

	@Test
	void recusaUfInvalida() {
		assertThatThrownBy(() -> service.update("kc-4", List.of(), List.of("SP", "XX")))
			.isInstanceOf(BadRequestException.class)
			.hasMessageContaining("XX");
	}

	@Test
	void recusaCargosDemais() {
		List<Long> many = java.util.stream.LongStream.rangeClosed(1, UserProfileService.MAX_POSITIONS + 1).boxed().toList();

		assertThatThrownBy(() -> service.update("kc-5", many, List.of())).isInstanceOf(BadRequestException.class);
	}

}
