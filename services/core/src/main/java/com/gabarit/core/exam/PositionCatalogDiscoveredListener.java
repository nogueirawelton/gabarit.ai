package com.gabarit.core.exam;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Consome position_catalog.discovered da fila core.position_catalog_discovered (declarada pela
 * infra). Mesmas regras de erro do {@link ExamDiscoveredListener}.
 */
@Component
class PositionCatalogDiscoveredListener {

	static final String QUEUE = "core.position_catalog_discovered";

	private static final Logger log = LoggerFactory.getLogger(PositionCatalogDiscoveredListener.class);

	private final CatalogPositionService catalogService;

	private final Validator validator;

	PositionCatalogDiscoveredListener(CatalogPositionService catalogService, Validator validator) {
		this.catalogService = catalogService;
		this.validator = validator;
	}

	@RabbitListener(queues = QUEUE)
	void on(PositionCatalogDiscoveredEvent event) {
		try (var _ = MDC.putCloseable("event_id", Objects.toString(event.eventId(), "-"));
				var _ = MDC.putCloseable("correlation_id", Objects.toString(event.correlationId(), "-"))) {
			handle(event);
		}
	}

	private void handle(PositionCatalogDiscoveredEvent event) {
		Set<ConstraintViolation<PositionCatalogDiscoveredEvent>> violations = validator.validate(event);
		if (!violations.isEmpty()) {
			throw reject("evento fora do contrato: " + violations.stream()
					.map(v -> v.getPropertyPath() + " " + v.getMessage())
					.sorted()
					.collect(Collectors.joining(", ")));
		}
		if (!event.isSupported()) {
			throw reject("evento não suportado: %s v%s".formatted(event.eventType(), event.version()));
		}

		CatalogPositionService.SyncResult result = catalogService.sync(event.data().source(),
				event.data().toEntries(), event.occurredAt());
		log.info("catálogo de cargos de {}: {} novos, {} atualizados, {} ignorados", event.data().source(),
				result.created(), result.updated(), result.ignored());
	}

	private static AmqpRejectAndDontRequeueException reject(String reason) {
		log.warn("{}, indo pra DLQ", reason);
		return new AmqpRejectAndDontRequeueException(reason);
	}

}
