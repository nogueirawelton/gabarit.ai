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
 * Consome exam.discovered da fila core.exam_discovered (declarada pela infra).
 *
 * <p>Erros:
 * <ul>
 * <li>JSON inválido, evento fora do contrato ou dados inválidos → DLQ direto (tentar de novo não resolve);
 * <li>qualquer outro (banco fora...) → a exceção sobe, a mensagem volta pra fila e, após 3 entregas,
 * vai pra DLQ (x-delivery-limit da fila).
 * </ul>
 * O ack só sai depois que o método retorna, ou seja, depois do commit do upsert.
 */
@Component
class ExamDiscoveredListener {

	static final String QUEUE = "core.exam_discovered";

	private static final Logger log = LoggerFactory.getLogger(ExamDiscoveredListener.class);

	private final ExamService examService;

	private final Validator validator;

	ExamDiscoveredListener(ExamService examService, Validator validator) {
		this.examService = examService;
		this.validator = validator;
	}

	@RabbitListener(queues = QUEUE)
	void on(ExamDiscoveredEvent event) {
		// no log estruturado (JSON no container) cada linha leva o evento e o comando de origem
		try (var _ = MDC.putCloseable("event_id", Objects.toString(event.eventId(), "-"));
				var _ = MDC.putCloseable("correlation_id", Objects.toString(event.correlationId(), "-"))) {
			handle(event);
		}
	}

	private void handle(ExamDiscoveredEvent event) {
		Set<ConstraintViolation<ExamDiscoveredEvent>> violations = validator.validate(event);
		if (!violations.isEmpty()) {
			throw reject("evento fora do contrato: " + violations.stream()
					.map(v -> v.getPropertyPath() + " " + v.getMessage())
					.sorted()
					.collect(Collectors.joining(", ")), null);
		}
		if (!event.isSupported()) {
			throw reject("evento não suportado: %s v%s".formatted(event.eventType(), event.version()), null);
		}

		ExamService.UpsertResult result;
		try {
			result = examService.upsert(event.data().toSnapshot(), event.occurredAt());
		}
		catch (IllegalArgumentException e) {
			throw reject("dados do concurso inválidos: " + e.getMessage(), e);
		}
		log.debug("exam.discovered {}: {}/{}", result, event.data().source(), event.data().externalId());
	}

	private static AmqpRejectAndDontRequeueException reject(String reason, Throwable cause) {
		log.warn("{}, indo pra DLQ", reason);
		return new AmqpRejectAndDontRequeueException(reason, cause);
	}

}
