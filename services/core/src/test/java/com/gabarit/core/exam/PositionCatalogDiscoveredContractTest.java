package com.gabarit.core.exam;

import com.gabarit.core.RepoFiles;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;

/** O DTO lê o que contracts/events/position_catalog.discovered.v1.json descreve. */
class PositionCatalogDiscoveredContractTest {

	static final JsonMapper MAPPER = JsonMapper.builder().build();

	static Schema schema;

	static JsonNode example;

	@BeforeAll
	static void load() throws IOException {
		String schemaJson = Files.readString(RepoFiles.path("contracts/events/position_catalog.discovered.v1.json"));
		schema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
			.getSchema(schemaJson, InputFormat.JSON);
		try (InputStream in = PositionCatalogDiscoveredContractTest.class
			.getResourceAsStream("/events/position_catalog.discovered.json")) {
			example = MAPPER.readTree(in);
		}
	}

	@Test
	void exemploBateComOSchema() {
		assertThat(schema.validate(example, ctx -> ctx.executionConfig(c -> c.formatAssertionsEnabled(true))))
			.isEmpty();
	}

	@Test
	void dtoLeOExemplo() {
		PositionCatalogDiscoveredEvent event = MAPPER.treeToValue(example, PositionCatalogDiscoveredEvent.class);

		assertThat(event.isSupported()).isTrue();
		assertThat(event.data().source()).isEqualTo("pciconcursos");
		assertThat(event.data().toEntries()).containsExactly(
				new CatalogPositionService.Entry("enfermeiro", "Enfermeiro"),
				new CatalogPositionService.Entry("tecnico-em-contabilidade", "Técnico em Contabilidade"),
				new CatalogPositionService.Entry("agente-de-endemias", "AGENTE DE ENDEMIAS"));
	}

}
