package com.gabarit.core.extraction;

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
 * Consome extraction.completed e extraction.failed da fila core.extraction_results (declarada pela
 * infra).
 *
 * <p>Erros: JSON inválido ou evento fora do contrato → DLQ direto; qualquer outro (banco fora...) →
 * volta pra fila e, após 3 entregas, DLQ. Evento repetido ou de pedido que já terminou é ignorado.
 */
@Component
class ExtractionResultListener {

	static final String QUEUE = "core.extraction_results";

	private static final Logger log = LoggerFactory.getLogger(ExtractionResultListener.class);

	private final ExtractionService service;

	private final Validator validator;

	ExtractionResultListener(ExtractionService service, Validator validator) {
		this.service = service;
		this.validator = validator;
	}

	@RabbitListener(queues = QUEUE)
	void on(ExtractionResultEvent event) {
		try (var _ = MDC.putCloseable("event_id", Objects.toString(event.eventId(), "-"));
				var _ = MDC.putCloseable("correlation_id", Objects.toString(event.correlationId(), "-"))) {
			handle(event);
		}
	}

	private void handle(ExtractionResultEvent event) {
		Set<ConstraintViolation<ExtractionResultEvent>> violations = validator.validate(event);
		if (!violations.isEmpty()) {
			throw reject("evento fora do contrato: " + violations.stream()
				.map(v -> v.getPropertyPath() + " " + v.getMessage())
				.sorted()
				.collect(Collectors.joining(", ")));
		}
		String problem = event.problem();
		if (problem != null) {
			throw reject(problem);
		}

		ExtractionResultEvent.Data data = event.data();
		if (event.isCompleted()) {
			service.complete(data.extractionId(), data.result().toString(), data.model(), data.usage().toUsage());
		}
		else {
			service.fail(data.extractionId(), data.reason(), data.retryable());
		}
	}

	private static AmqpRejectAndDontRequeueException reject(String reason) {
		log.warn("{}, indo pra DLQ", reason);
		return new AmqpRejectAndDontRequeueException(reason);
	}

}
