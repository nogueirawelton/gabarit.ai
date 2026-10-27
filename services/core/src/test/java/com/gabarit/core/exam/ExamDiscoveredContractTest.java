package com.gabarit.core.exam;

import com.gabarit.core.RepoFiles;
import com.networknt.schema.Error;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** O DTO lê o que o contrato (contracts/, compartilhado com o scraper) descreve. */
class ExamDiscoveredContractTest {

	static final JsonMapper MAPPER = JsonMapper.builder().build();

	static Schema schema;

	static JsonNode example;

	@BeforeAll
	static void load() throws IOException {
		String schemaJson = Files.readString(RepoFiles.path("contracts/events/exam.discovered.v1.json"));
		schema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
			.getSchema(schemaJson, InputFormat.JSON);
		try (InputStream in = ExamDiscoveredContractTest.class.getResourceAsStream("/events/exam.discovered.json")) {
			example = MAPPER.readTree(in);
		}
	}

	static List<Error> validate(JsonNode node) {
		// format (uuid, date-time, date) é só anotação no draft 2020-12; aqui vira regra
		return schema.validate(node, ctx -> ctx.executionConfig(c -> c.formatAssertionsEnabled(true)));
	}

	@Test
	void exemploBateComOSchema() {
		assertThat(validate(example)).isEmpty();
	}

	@Test
	void schemaRecusaEventoSemCampoObrigatorio() {
		ObjectNode broken = (ObjectNode) example.deepCopy();
		((ObjectNode) broken.get("data")).remove("external_id");

		assertThat(validate(broken)).isNotEmpty();
	}

	@Test
	void dtoLeOExemplo() {
		ExamDiscoveredEvent event = MAPPER.treeToValue(example, ExamDiscoveredEvent.class);

		assertThat(event.isSupported()).isTrue();
		assertThat(event.occurredAt()).isEqualTo(Instant.parse("2026-10-27T14:22:00.216338Z"));
		assertThat(event.correlationId()).isEqualTo("eed88739-2c9a-4e1e-9a47-98785717fdb6");

		ExamSnapshot snapshot = event.data().toSnapshot();
		assertThat(snapshot.externalId()).isEqualTo("295975");
		assertThat(snapshot.educationLevel()).isEqualTo("Médio / Superior");
		assertThat(snapshot.registrationStart()).isEqualTo(LocalDate.of(2026, 10, 28));
		assertThat(snapshot.registrationNote()).isNull();
		assertThat(snapshot.positions()).containsExactly(
				new ExamSnapshot.Position("ESCREVENTE TÉCNICO JUDICIÁRIO", List.of("escrevente", "tecnico-judiciario")),
				new ExamSnapshot.Position("OFICIAL DE JUSTIÇA", List.of("oficial-de-justica")));
	}

	@Test
	void catalogSlugsEOpcional() {
		// eventos de antes do catálogo não têm o campo: continuam válidos e viram lista vazia
		ObjectNode old = (ObjectNode) example.deepCopy();
		old.get("data").get("positions").forEach(p -> ((ObjectNode) p).remove("catalog_slugs"));

		assertThat(validate(old)).isEmpty();
		ExamSnapshot snapshot = MAPPER.treeToValue(old, ExamDiscoveredEvent.class).data().toSnapshot();
		assertThat(snapshot.positions()).allSatisfy(p -> assertThat(p.catalogSlugs()).isEmpty());
	}

	@Test
	void campoNovoNoEventoEIgnorado() {
		// leitor tolerante: o scraper pode adicionar campo sem quebrar o core
		ObjectNode extended = (ObjectNode) example.deepCopy();
		extended.put("novo_campo", "x");

		assertThat(MAPPER.treeToValue(extended, ExamDiscoveredEvent.class).eventId()).isNotNull();
	}

}
