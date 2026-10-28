package com.gabarit.core.exam;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Collator;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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

	// ordem alfabética do português (ignora acento e caixa: "Ágil" junto de "agente")
	private static final Comparator<CatalogPosition> BY_NAME = Comparator.comparing(CatalogPosition::getName,
			collator());

	private final CatalogPositionRepository repository;

	CatalogPositionService(CatalogPositionRepository repository) {
		this.repository = repository;
	}

	/** Catálogo de uma fonte em ordem alfabética. */
	@Transactional(readOnly = true)
	public List<CatalogPosition> list(String source) {
		return repository.findBySource(source).stream().sorted(BY_NAME).toList();
	}

	/** Os cargos com esses ids, em ordem alfabética. Id que não existe é ignorado. */
	@Transactional(readOnly = true)
	public List<CatalogPosition> findAll(Collection<Long> ids) {
		return repository.findAllById(ids).stream().sorted(BY_NAME).toList();
	}

	private static Collator collator() {
		Collator collator = Collator.getInstance(Locale.forLanguageTag("pt-BR"));
		collator.setStrength(Collator.PRIMARY);
		return collator;
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
