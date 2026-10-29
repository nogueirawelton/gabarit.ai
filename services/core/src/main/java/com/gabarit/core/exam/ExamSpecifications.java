package com.gabarit.core.exam;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Nulls;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.springframework.data.jpa.domain.Specification;

import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Busca de concursos com filtros opcionais: um predicado por filtro presente, todos com "and".
 * Melhor que uma query fixa com {@code (:uf IS NULL OR uf = :uf)}, que no Postgres atrapalha o
 * planner e dá erro de tipo com parâmetro nulo.
 */
final class ExamSpecifications {

	private static final char ESCAPE = '\\';

	private ExamSpecifications() {
	}

	/**
	 * Filtro + ordem fixa: abertos primeiro, depois os que vão abrir, sem data e encerrados; dentro
	 * de cada grupo, o fim das inscrições mais próximo primeiro. A ordem não vem do cliente (campo
	 * arbitrário vira 500 ou expõe nome de coluna).
	 */
	static Specification<Exam> matching(ExamFilter filter, LocalDate today) {
		return (exam, query, cb) -> {
			List<Predicate> where = new ArrayList<>();
			if (!filter.ufs().isEmpty()) {
				// nacional (sem UF) vale para qualquer estado
				where.add(cb.or(exam.get("uf").in(filter.ufs()), cb.isNull(exam.get("uf"))));
			}
			if (!filter.statuses().isEmpty()) {
				where.add(cb.or(filter.statuses()
					.stream()
					.map(status -> status(status, exam, cb, today))
					.toArray(Predicate[]::new)));
			}
			if (!filter.educationLevels().isEmpty()) {
				where.add(educationLevels(filter.educationLevels(), exam, cb));
			}
			if (filter.q() != null) {
				// unaccent (extensão, V7) no título; o texto digitado já vai sem acento daqui
				Expression<String> title = cb.function("unaccent", String.class, cb.lower(exam.get("title")));
				where.add(cb.like(title, "%" + escapeLike(withoutAccents(filter.q())) + "%", ESCAPE));
			}
			if (filter.followsPositions()) {
				where.add(followsPositions(filter.catalogSlugs(), exam, query, cb));
			}
			// na query de contagem o SimpleJpaRepository descarta o orderBy
			query.orderBy(cb.asc(statusRank(exam, cb, today)), cb.asc(exam.get("registrationEnd"), Nulls.LAST),
					cb.asc(exam.get("id")));
			return cb.and(where.toArray(Predicate[]::new));
		};
	}

	/** Espelho em SQL de {@link ExamStatus#of}: os quatro são exclusivos (o banco garante fim >= início). */
	static Predicate status(ExamStatus status, Root<Exam> exam, CriteriaBuilder cb, LocalDate today) {
		Path<LocalDate> start = exam.get("registrationStart");
		Path<LocalDate> end = exam.get("registrationEnd");
		return switch (status) {
			case UPCOMING -> cb.greaterThan(start, today);
			case CLOSED -> cb.lessThan(end, today);
			case UNKNOWN -> cb.and(cb.isNull(start), cb.isNull(end));
			case OPEN -> cb.and(cb.or(cb.isNull(start), cb.lessThanOrEqualTo(start, today)),
					cb.or(cb.greaterThanOrEqualTo(end, today), cb.and(cb.isNull(end), cb.isNotNull(start))));
		};
	}

	private static Expression<Integer> statusRank(Root<Exam> exam, CriteriaBuilder cb, LocalDate today) {
		return cb.<Integer>selectCase()
			.when(status(ExamStatus.OPEN, exam, cb, today), 0)
			.when(status(ExamStatus.UPCOMING, exam, cb, today), 1)
			.when(status(ExamStatus.UNKNOWN, exam, cb, today), 2)
			.otherwise(3);
	}

	/**
	 * Algum cargo ativo do concurso está em algum dos cargos do catálogo seguidos (mesma fonte do
	 * concurso: slug só vale dentro da fonte). Vira {@code catalog_slugs && ARRAY[...]}, que usa o
	 * índice GIN da V2.
	 */
	private static Predicate followsPositions(Map<String, Set<String>> slugsBySource, Root<Exam> exam,
			CriteriaQuery<?> query, CriteriaBuilder cb) {
		if (slugsBySource.isEmpty()) {
			return cb.disjunction();
		}
		var hcb = (HibernateCriteriaBuilder) cb;
		Subquery<Integer> positions = query.subquery(Integer.class);
		Root<ExamPosition> position = positions.from(ExamPosition.class);
		// List<String> mapeada como text[]: as funções de array do Hibernate aceitam, o tipo Java é que não bate
		@SuppressWarnings("unchecked")
		Expression<String[]> catalogSlugs = (Expression<String[]>) (Expression<?>) position.get("catalogSlugs");
		Predicate anySource = cb.or(slugsBySource.entrySet()
			.stream()
			.map(e -> cb.and(cb.equal(exam.get("source"), e.getKey()),
					hcb.arrayIntersects(catalogSlugs, e.getValue().toArray(String[]::new))))
			.toArray(Predicate[]::new));
		positions.select(cb.literal(1))
			.where(cb.equal(position.get("exam"), exam), cb.isTrue(position.get("active")), anySource);
		return cb.exists(positions);
	}

	/** Algum nível em comum; concurso sem escolaridade informada (array vazio) entra sempre. */
	private static Predicate educationLevels(Set<EducationLevel> levels, Root<Exam> exam, CriteriaBuilder cb) {
		var hcb = (HibernateCriteriaBuilder) cb;
		@SuppressWarnings("unchecked")
		Expression<String[]> examLevels = (Expression<String[]>) (Expression<?>) exam.get("educationLevels");
		return cb.or(hcb.arrayIntersects(examLevels, levels.stream().map(Enum::name).toArray(String[]::new)),
				// array_length de array vazio é NULL no Postgres; primeiro elemento nulo = vazio
				cb.isNull(hcb.arrayGet(examLevels, 1)));
	}

	private static String withoutAccents(String text) {
		return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
	}

	// "100%" digitado pelo usuário é texto, não curinga
	private static String escapeLike(String text) {
		return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}

}
