package com.gabarit.core.exam;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Evento position_catalog.discovered v1, publicado pelo scraper antes dos exam.discovered.
 * Espelha contracts/events/position_catalog.discovered.v1.json; campo extra é ignorado.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
record PositionCatalogDiscoveredEvent(
		@NotNull UUID eventId,
		@NotNull String eventType,
		@NotNull Integer version,
		@NotNull Instant occurredAt,
		String correlationId,
		@NotNull @Valid Data data) {

	static final String TYPE = "position_catalog.discovered";

	static final int VERSION = 1;

	boolean isSupported() {
		return TYPE.equals(eventType) && version == VERSION;
	}

	record Data(@NotBlank String source, @NotNull List<@NotNull @Valid Position> positions) {

		List<CatalogPositionService.Entry> toEntries() {
			return positions.stream().map(p -> new CatalogPositionService.Entry(p.slug(), p.name())).toList();
		}

	}

	record Position(@NotBlank String slug, @NotBlank String name) {
	}

}
