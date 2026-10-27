package com.gabarit.core.exam;

import com.gabarit.core.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Ponta a ponta no core: publica no gabarit.events (como o scraper) com a topologia real da infra,
 * e confere o banco e a DLQ.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ExamDiscoveredListenerTest {

	static final String EVENTS_EXCHANGE = "gabarit.events";

	static final String DLQ = ExamDiscoveredListener.QUEUE + ".dlq";

	static final Duration TIMEOUT = Duration.ofSeconds(10);

	@Autowired
	RabbitTemplate rabbit;

	@Autowired
	ExamRepository repository;

	@Autowired
	JsonMapper mapper;

	/** O exemplo do contrato, com outro external_id para os testes não se misturarem. */
	ObjectNode event(String externalId, String occurredAt, String title) throws IOException {
		try (InputStream in = getClass().getResourceAsStream("/events/exam.discovered.json")) {
			ObjectNode node = (ObjectNode) mapper.readTree(in);
			node.put("event_id", UUID.randomUUID().toString());
			node.put("occurred_at", occurredAt);
			ObjectNode data = (ObjectNode) node.get("data");
			data.put("external_id", externalId);
			data.put("title", title);
			return node;
		}
	}

	void publish(String body) {
		MessageProperties props = new MessageProperties();
		props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
		rabbit.send(EVENTS_EXCHANGE, ExamDiscoveredEvent.TYPE, new Message(body.getBytes(StandardCharsets.UTF_8), props));
	}

	Optional<Exam> find(String externalId) {
		return repository.findBySourceAndExternalId("pciconcursos", externalId);
	}

	@Test
	void eventoViraConcursoComCargos() throws IOException {
		publish(event("t-1", "2026-10-27T10:00:00Z", "TJ SP").toString());

		await().atMost(TIMEOUT).untilAsserted(() -> assertThat(find("t-1")).isPresent());
		Exam exam = find("t-1").orElseThrow();
		assertThat(exam.getTitle()).isEqualTo("TJ SP");
		assertThat(exam.getActivePositions()).extracting(ExamPosition::getName)
			.containsExactly("ESCREVENTE TÉCNICO JUDICIÁRIO", "OFICIAL DE JUSTIÇA");
	}

	@Test
	void mesmoConcursoDeNovoAtualizaSemDuplicarEEventoAntigoNaoSobrescreve() throws IOException {
		publish(event("t-2", "2026-10-27T10:00:00Z", "v1").toString());
		await().atMost(TIMEOUT).untilAsserted(() -> assertThat(find("t-2")).isPresent());

		publish(event("t-2", "2026-10-27T16:00:00Z", "v2").toString());
		await().atMost(TIMEOUT).untilAsserted(() -> assertThat(find("t-2").orElseThrow().getTitle()).isEqualTo("v2"));

		// chega atrasado (mais antigo que o já aplicado): ignorado
		publish(event("t-2", "2026-10-27T12:00:00Z", "atrasado").toString());
		// um evento depois dele, na mesma fila: quando este aparece, o atrasado já foi processado
		publish(event("t-2-marker", "2026-10-27T12:00:00Z", "marker").toString());
		await().atMost(TIMEOUT).untilAsserted(() -> assertThat(find("t-2-marker")).isPresent());

		assertThat(find("t-2").orElseThrow().getTitle()).isEqualTo("v2");
		assertThat(repository.findAll()).filteredOn(e -> e.getExternalId().equals("t-2")).hasSize(1);
	}

	@Test
	void versaoDesconhecidaVaiPraDlq() throws IOException {
		ObjectNode node = event("t-3", "2026-10-27T10:00:00Z", "v2 do contrato");
		node.put("version", 2);

		publish(node.toString());

		Message dead = rabbit.receive(DLQ, TIMEOUT.toMillis());
		assertThat(dead).isNotNull();
		assertThat(new String(dead.getBody(), StandardCharsets.UTF_8)).contains("t-3");
		assertThat(find("t-3")).isEmpty();
	}

	@Test
	void jsonQuebradoVaiPraDlq() {
		publish("{isso não é json");

		Message dead = rabbit.receive(DLQ, TIMEOUT.toMillis());
		assertThat(dead).isNotNull();
		assertThat(new String(dead.getBody(), StandardCharsets.UTF_8)).startsWith("{isso");
	}

}
