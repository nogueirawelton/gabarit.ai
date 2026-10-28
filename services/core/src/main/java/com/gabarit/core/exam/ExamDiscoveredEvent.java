package com.gabarit.core.exam;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Evento exam.discovered v1, publicado pelo scraper.
 * Espelha contracts/events/exam.discovered.v1.json (fonte da verdade); campo extra é ignorado.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
record ExamDiscoveredEvent(
		@NotNull UUID eventId,
		@NotNull String eventType,
		@NotNull Integer version,
		@NotNull Instant occurredAt,
		// message_id do comando que originou a coleta
		String correlationId,
		@NotNull @Valid Data data) {

	static final String TYPE = "exam.discovered";

	static final int VERSION = 1;

	boolean isSupported() {
		return TYPE.equals(eventType) && version == VERSION;
	}

	@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
	record Data(
			@NotBlank String source,
			@NotBlank String externalId,
			@NotBlank String title,
			@NotBlank String url,
			String uf,
			String region,
			String educationLevel,
			String openings,
			LocalDate registrationStart,
			LocalDate registrationEnd,
			String registrationNote,
			@NotNull List<@NotNull @Valid Position> positions) {

		ExamSnapshot toSnapshot() {
			return new ExamSnapshot(source, externalId, title, url, uf, region, educationLevel, openings,
					registrationStart, registrationEnd, registrationNote,
					positions.stream().map(p -> new ExamSnapshot.Position(p.name(), p.catalogSlugs())).toList());
		}

	}

	/** {@code catalog_slugs} é opcional no contrato: ausente vira lista vazia. */
	@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
	record Position(@NotBlank String name, List<@NotBlank String> catalogSlugs) {
	}

}
