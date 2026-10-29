package com.gabarit.core.extraction;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.time.Instant;
import java.util.UUID;

/**
 * Eventos extraction.completed v1 e extraction.failed v1, publicados pelo worker extraction na
 * mesma fila. Espelha contracts/events/extraction.{completed,failed}.v1.json (fonte da verdade);
 * campo extra é ignorado. Os campos de {@code data} que só um dos dois tem são opcionais aqui e
 * conferidos por {@link #problem()}.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
record ExtractionResultEvent(
		@NotNull UUID eventId,
		@NotNull String eventType,
		@NotNull Integer version,
		@NotNull Instant occurredAt,
		// message_id do extraction.requested
		String correlationId,
		@NotNull @Valid Data data) {

	static final String COMPLETED = "extraction.completed";

	static final String FAILED = "extraction.failed";

	static final int VERSION = 1;

	boolean isCompleted() {
		return COMPLETED.equals(eventType);
	}

	/** O que impede de aplicar o evento, ou nulo se está ok. */
	String problem() {
		if (version != VERSION || !(COMPLETED.equals(eventType) || FAILED.equals(eventType))) {
			return "evento não suportado: %s v%s".formatted(eventType, version);
		}
		if (isCompleted() && (data.result() == null || !data.result().isObject() || data.usage() == null)) {
			return "extraction.completed sem result ou usage";
		}
		if (!isCompleted() && (data.reason() == null || data.reason().isBlank() || data.retryable() == null)) {
			return "extraction.failed sem reason ou retryable";
		}
		return null;
	}

	@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
	record Data(
			@NotNull UUID extractionId,
			@NotBlank String task,
			@NotBlank String promptVersion,
			// completed
			String model,
			JsonNode result,
			@Valid Usage usage,
			// failed
			String reason,
			Boolean retryable) {
	}

	@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
	record Usage(
			@NotNull @PositiveOrZero Integer inputTokens,
			@NotNull @PositiveOrZero Integer outputTokens,
			@NotNull @PositiveOrZero Integer cacheReadInputTokens,
			@NotNull @PositiveOrZero Integer cacheCreationInputTokens) {

		Extraction.Usage toUsage() {
			return new Extraction.Usage(inputTokens, outputTokens, cacheReadInputTokens, cacheCreationInputTokens);
		}

	}

}
