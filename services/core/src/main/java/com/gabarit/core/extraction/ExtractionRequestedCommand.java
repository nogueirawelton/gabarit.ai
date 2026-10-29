package com.gabarit.core.extraction;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Comando extraction.requested v1, consumido pelo worker extraction.
 * Espelha contracts/commands/extraction.requested.v1.json (fonte da verdade).
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
record ExtractionRequestedCommand(UUID extractionId, ExtractionTask task, String promptVersion,
		List<Document> documents, Map<String, Object> params) {

	static final String ROUTING_KEY = "extraction.requested";

	record Document(String sha256, String key) {
	}

}
