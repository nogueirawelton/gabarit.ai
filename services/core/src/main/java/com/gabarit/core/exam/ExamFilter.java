package com.gabarit.core.exam;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Filtros da busca de concursos. Vazio/nulo = sem filtro, exceto {@code catalogSlugs}.
 *
 * @param ufs concursos dessas UFs; nacionais (sem UF) entram sempre
 * @param statuses situação das inscrições hoje
 * @param educationLevels concursos com vaga em algum desses níveis; sem escolaridade informada entram sempre
 * @param q trecho do título, sem diferença de acento e caixa
 * @param catalogSlugs cargos do catálogo por fonte: só concursos com cargo ativo em algum deles.
 * {@code null} = sem filtro; mapa vazio = nenhum concurso
 */
public record ExamFilter(Set<String> ufs, Set<ExamStatus> statuses, Set<EducationLevel> educationLevels, String q,
		Map<String, Set<String>> catalogSlugs) {

	public ExamFilter {
		ufs = ufs == null ? Set.of()
				: ufs.stream().map(uf -> uf.trim().toUpperCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
		statuses = statuses == null ? Set.of() : Set.copyOf(statuses);
		educationLevels = educationLevels == null ? Set.of() : Set.copyOf(educationLevels);
		q = q == null || q.isBlank() ? null : q.trim();
		catalogSlugs = catalogSlugs == null ? null : Map.copyOf(catalogSlugs);
	}

	public static ExamFilter all() {
		return new ExamFilter(null, null, null, null, null);
	}

	boolean followsPositions() {
		return catalogSlugs != null;
	}

}
