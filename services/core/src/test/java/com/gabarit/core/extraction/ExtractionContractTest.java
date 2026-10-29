package com.gabarit.core.extraction;

import com.gabarit.core.RepoFiles;
import com.networknt.schema.Error;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Comando e eventos batem com os schemas em contracts/ (compartilhados com o worker extraction). */
class ExtractionContractTest {

	static final JsonMapper MAPPER = JsonMapper.builder().build();

	static final String SHA = "2c26b46b68ffc68ff99b453c1d30413413422d706483bfa0f98a5e886266e7ae";

	static List<Error> validate(String contract, JsonNode node) throws IOException {
		Schema schema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
			.getSchema(Files.readString(RepoFiles.path("contracts/" + contract)), InputFormat.JSON);
		// format (uuid, date-time) é só anotação no draft 2020-12; aqui vira regra
		return schema.validate(node, ctx -> ctx.executionConfig(c -> c.formatAssertionsEnabled(true)));
	}

	static ObjectNode example(String name) throws IOException {
		try (InputStream in = ExtractionContractTest.class.getResourceAsStream("/events/" + name)) {
			return (ObjectNode) MAPPER.readTree(in);
		}
	}

	@Test
	void comandoBateComOSchema() throws IOException {
		var command = new ExtractionRequestedCommand(UUID.randomUUID(), ExtractionTask.ECHO, "v1",
				List.of(new ExtractionRequestedCommand.Document(SHA, "blobs/" + SHA + ".pdf")),
				Map.of("cargo", "Analista", "anos", List.of(2019, 2022)));

		assertThat(validate("commands/extraction.requested.v1.json", MAPPER.valueToTree(command))).isEmpty();
	}

	@Test
	void exemplosBatemComOSchema() throws IOException {
		assertThat(validate("events/extraction.completed.v1.json", example("extraction.completed.json"))).isEmpty();
		assertThat(validate("events/extraction.failed.v1.json", example("extraction.failed.json"))).isEmpty();
	}

	@Test
	void dtoLeOsExemplos() throws IOException {
		ExtractionResultEvent completed = MAPPER.treeToValue(example("extraction.completed.json"),
				ExtractionResultEvent.class);
		assertThat(completed.problem()).isNull();
		assertThat(completed.isCompleted()).isTrue();
		assertThat(completed.data().result().get("params").get("cargo").asString()).isEqualTo("Analista");
		assertThat(completed.data().usage().toUsage()).isEqualTo(new Extraction.Usage(0, 0, 0, 0));

		ExtractionResultEvent failed = MAPPER.treeToValue(example("extraction.failed.json"), ExtractionResultEvent.class);
		assertThat(failed.problem()).isNull();
		assertThat(failed.isCompleted()).isFalse();
		assertThat(failed.data().retryable()).isFalse();
	}

	@Test
	void eventoIncompletoTemProblema() throws IOException {
		ObjectNode completed = example("extraction.completed.json");
		((ObjectNode) completed.get("data")).remove("result");
		ObjectNode failed = example("extraction.failed.json");
		((ObjectNode) failed.get("data")).remove("retryable");
		ObjectNode future = example("extraction.failed.json");
		future.put("version", 2);

		assertThat(MAPPER.treeToValue(completed, ExtractionResultEvent.class).problem()).contains("sem result");
		assertThat(MAPPER.treeToValue(failed, ExtractionResultEvent.class).problem()).contains("sem reason");
		assertThat(MAPPER.treeToValue(future, ExtractionResultEvent.class).problem()).contains("não suportado");
	}

}
