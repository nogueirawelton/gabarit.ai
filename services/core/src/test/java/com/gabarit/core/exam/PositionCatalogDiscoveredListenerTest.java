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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** Publica no gabarit.events (como o scraper), com a topologia real, e confere o banco e a DLQ. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PositionCatalogDiscoveredListenerTest {

	static final String DLQ = PositionCatalogDiscoveredListener.QUEUE + ".dlq";

	static final Duration TIMEOUT = Duration.ofSeconds(10);

	@Autowired
	RabbitTemplate rabbit;

	@Autowired
	CatalogPositionRepository repository;

	@Autowired
	JsonMapper mapper;

	/** O exemplo do contrato, com uma fonte própria para os testes não se misturarem. */
	ObjectNode event(String source) throws IOException {
		try (InputStream in = getClass().getResourceAsStream("/events/position_catalog.discovered.json")) {
			ObjectNode node = (ObjectNode) mapper.readTree(in);
			node.put("event_id", UUID.randomUUID().toString());
			((ObjectNode) node.get("data")).put("source", source);
			return node;
		}
	}

	void publish(String body) {
		MessageProperties props = new MessageProperties();
		props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
		rabbit.send("gabarit.events", PositionCatalogDiscoveredEvent.TYPE,
				new Message(body.getBytes(StandardCharsets.UTF_8), props));
	}

	@Test
	void catalogoViraCargosNoBanco() throws IOException {
		publish(event("t-catalogo").toString());

		await().atMost(TIMEOUT).untilAsserted(() -> assertThat(repository.findBySource("t-catalogo")).hasSize(3));
		assertThat(repository.findBySource("t-catalogo")).extracting(CatalogPosition::getSlug)
			.containsExactlyInAnyOrder("enfermeiro", "tecnico-em-contabilidade", "agente-de-endemias");
	}

	@Test
	void cargoSemSlugVaiPraDlq() throws IOException {
		ObjectNode node = event("t-catalogo-invalido");
		((ObjectNode) node.get("data").get("positions").get(0)).put("slug", "");

		publish(node.toString());

		Message dead = rabbit.receive(DLQ, TIMEOUT.toMillis());
		assertThat(dead).isNotNull();
		assertThat(repository.findBySource("t-catalogo-invalido")).isEmpty();
	}

}
