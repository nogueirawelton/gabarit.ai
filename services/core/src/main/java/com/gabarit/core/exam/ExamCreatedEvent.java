package com.gabarit.core.exam;

import java.util.Set;
import java.util.UUID;

/**
 * Evento interno (Spring, não RabbitMQ): um concurso apareceu pela primeira vez. Publicado dentro
 * da transação que o criou: quem ouve com {@code @EventListener} grava junto (ou nada é gravado).
 * Leva o que é preciso para casar com perfis, sem o ouvinte depender do repositório de concursos.
 *
 * @param catalogSlugs slugs do catálogo dos cargos ativos, da mesma {@code source}
 */
public record ExamCreatedEvent(UUID examId, String source, String uf, Set<EducationLevel> educationLevels,
		Set<String> catalogSlugs, ExamStatus status) {
}
