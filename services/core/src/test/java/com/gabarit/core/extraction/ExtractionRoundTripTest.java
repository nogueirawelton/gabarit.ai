package com.gabarit.core.extraction;

import com.gabarit.core.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.awaitility.Awaitility.await;

/**
 * Caminho core → worker → core com a topologia real da infra: o teste faz o papel do worker
 * (lê extraction.requests e publica a resposta em gabarit.events).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ExtractionRoundTripTest {

	static final String REQUESTS = "extraction.requests";

	static final String EVENTS_EXCHANGE = "gabarit.events";

	static final String RESULTS_DLQ = ExtractionResultListener.QUEUE + ".dlq";

	static final String SHA = "2c26b46b68ffc68ff99b453c1d30413413422d706483bfa0f98a5e886266e7ae";

	static final Duration TIMEOUT = Duration.ofSeconds(10);

	@Autowired
	ExtractionService service;

	@Autowired
	ExtractionRepository repository;

	@Autowired
	RabbitTemplate rabbit;

	@Autowired
	JsonMapper mapper;

	/** Cada teste com parâmetros próprios: chaves de cache diferentes. */
	Extraction request(String test) {
		return service.request(ExtractionTask.ECHO, List.of(SHA), Map.of("test", test));
	}

	JsonNode receiveRequest() {
		Message message = rabbit.receive(REQUESTS, TIMEOUT.toMillis());
		assertThat(message).as("extraction.requested publicado").isNotNull();
		assertThat(message.getMessageProperties().getMessageId()).isNotBlank();
		return mapper.readTree(new String(message.getBody(), StandardCharsets.UTF_8));
	}

	void assertNoRequest() {
		assertThat(rabbit.receive(REQUESTS, 500)).as("nada publicado").isNull();
	}

	void reply(String example, UUID extractionId, Consumer<ObjectNode> change) throws IOException {
		try (InputStream in = getClass().getResourceAsStream("/events/" + example)) {
			ObjectNode event = (ObjectNode) mapper.readTree(in);
			event.put("event_id", UUID.randomUUID().toString());
			((ObjectNode) event.get("data")).put("extraction_id", extractionId.toString());
			change.accept(event);
			MessageProperties props = new MessageProperties();
			props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
			rabbit.send(EVENTS_EXCHANGE, event.get("event_type").asString(),
					new Message(event.toString().getBytes(StandardCharsets.UTF_8), props));
		}
	}

	Extraction.Status status(UUID id) {
		return repository.findById(id).orElseThrow().getStatus();
	}

	@Test
	void pedidoVaiAoWorkerEResultadoVolta() throws IOException {
		Extraction extraction = request("ida-e-volta");

		assertThat(extraction.getStatus()).isEqualTo(Extraction.Status.PENDING);
		JsonNode command = receiveRequest();
		assertThat(command.get("extraction_id").asString()).isEqualTo(extraction.getId().toString());
		assertThat(command.get("task").asString()).isEqualTo("ECHO");
		assertThat(command.get("prompt_version").asString()).isEqualTo("v1");
		assertThat(command.get("documents").get(0).get("key").asString()).isEqualTo("blobs/" + SHA + ".pdf");
		assertThat(command.get("params").get("test").asString()).isEqualTo("ida-e-volta");

		reply("extraction.completed.json", extraction.getId(), event -> {
		});

		await().atMost(TIMEOUT).untilAsserted(() -> assertThat(status(extraction.getId())).isEqualTo(Extraction.Status.DONE));
		Extraction done = repository.findById(extraction.getId()).orElseThrow();
		assertThat(mapper.readTree(done.getResult()).get("params").get("cargo").asString()).isEqualTo("Analista");
		assertThat(done.getCompletedAt()).isNotNull();
	}

	@Test
	void pedidoIgualNaoPublicaDeNovo() {
		TreeMap<String, Object> params = new TreeMap<>(Map.of("test", "cache", "cargo", "Analista"));
		Extraction first = service.request(ExtractionTask.ECHO, List.of(SHA), params);
		receiveRequest();

		// pendente: só aguarda. Parâmetros em outra ordem são a mesma entrada
		Extraction same = service.request(ExtractionTask.ECHO, List.of(SHA), params.descendingMap());
		assertThat(same.getId()).isEqualTo(first.getId());
		assertNoRequest();
	}

	@Test
	void falhaTransitoriaEPedidaDeNovoEDefinitivaNao() throws IOException {
		Extraction transient_ = request("falha-transitoria");
		Extraction permanent = request("falha-definitiva");
		receiveRequest();
		receiveRequest();

		reply("extraction.failed.json", transient_.getId(), e -> ((ObjectNode) e.get("data")).put("retryable", true));
		reply("extraction.failed.json", permanent.getId(), e -> {
		});
		await().atMost(TIMEOUT).untilAsserted(() -> {
			assertThat(status(transient_.getId())).isEqualTo(Extraction.Status.FAILED);
			assertThat(status(permanent.getId())).isEqualTo(Extraction.Status.FAILED);
		});

		assertThat(request("falha-definitiva").getStatus()).isEqualTo(Extraction.Status.FAILED);
		assertNoRequest();

		Extraction retried = request("falha-transitoria");
		assertThat(retried.getId()).isEqualTo(transient_.getId());
		assertThat(retried.getStatus()).isEqualTo(Extraction.Status.PENDING);
		assertThat(receiveRequest().get("extraction_id").asString()).isEqualTo(transient_.getId().toString());
	}

	@Test
	void resultadoRepetidoNaoMudaNada() throws IOException {
		Extraction extraction = request("repetido");
		receiveRequest();

		reply("extraction.completed.json", extraction.getId(), event -> {
		});
		await().atMost(TIMEOUT).untilAsserted(() -> assertThat(status(extraction.getId())).isEqualTo(Extraction.Status.DONE));

		// chega um failed atrasado: o DONE fica
		reply("extraction.failed.json", extraction.getId(), event -> ((ObjectNode) event.get("data")).put("retryable", true));
		Extraction marker = request("repetido-marcador");
		receiveRequest();
		reply("extraction.completed.json", marker.getId(), event -> {
		});
		// mesma fila: quando o marcador termina, o atrasado já foi processado
		await().atMost(TIMEOUT).untilAsserted(() -> assertThat(status(marker.getId())).isEqualTo(Extraction.Status.DONE));

		assertThat(status(extraction.getId())).isEqualTo(Extraction.Status.DONE);
	}

	@Test
	void resultadoForaDoContratoVaiPraDlq() throws IOException {
		Extraction extraction = request("dlq");
		receiveRequest();

		reply("extraction.completed.json", extraction.getId(), event -> ((ObjectNode) event.get("data")).remove("usage"));

		Message dead = rabbit.receive(RESULTS_DLQ, TIMEOUT.toMillis());
		assertThat(dead).isNotNull();
		assertThat(new String(dead.getBody(), StandardCharsets.UTF_8)).contains(extraction.getId().toString());
		assertThat(status(extraction.getId())).isEqualTo(Extraction.Status.PENDING);
	}

	@Test
	void hashInvalidoERecusado() {
		assertThatIllegalArgumentException().isThrownBy(() -> service.request(ExtractionTask.ECHO, List.of("nao-e-hash"), Map.of()));
	}

}
