package com.gabarit.core.exam;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Catálogo de cargos das fontes. */
@Service
public class CatalogPositionService {

	public record Entry(String slug, String name) {
	}

	public record SyncResult(int created, int updated, int ignored) {
	}

	private final CatalogPositionRepository repository;

	CatalogPositionService(CatalogPositionRepository repository) {
		this.repository = repository;
	}

	/**
	 * Upsert do catálogo de uma fonte por (source, slug). Cargo que não veio no evento fica como
	 * está: concursos antigos podem apontar para ele. Idempotente.
	 */
	@Transactional
	public SyncResult sync(String source, List<Entry> entries, Instant occurredAt) {
		Map<String, CatalogPosition> current = repository.findBySource(source)
				.stream()
				.collect(Collectors.toMap(CatalogPosition::getSlug, Function.identity()));
		// slug repetido no evento: o último ganha
		Map<String, String> wanted = new LinkedHashMap<>();
		entries.forEach(e -> wanted.put(e.slug(), e.name()));

		int created = 0;
		int updated = 0;
		int ignored = 0;
		for (Map.Entry<String, String> e : wanted.entrySet()) {
			CatalogPosition existing = current.get(e.getKey());
			if (existing == null) {
				repository.save(new CatalogPosition(source, e.getKey(), e.getValue(), occurredAt));
				created++;
			}
			else if (existing.apply(e.getValue(), occurredAt)) {
				updated++;
			}
			else {
				ignored++;
			}
		}
		return new SyncResult(created, updated, ignored);
	}

}
