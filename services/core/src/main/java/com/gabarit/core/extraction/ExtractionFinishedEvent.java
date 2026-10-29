package com.gabarit.core.extraction;

import java.util.UUID;

/**
 * Evento interno (Spring, não RabbitMQ): uma leitura terminou (DONE ou FAILED). Publicado dentro da
 * transação que gravou o resultado: quem ouve com {@code @EventListener} grava junto. Quem pediu
 * (ex.: o study, para o edital de um alvo) acha os seus pedidos pelo {@code extractionId} que o
 * {@link ExtractionService#request} devolveu.
 */
public record ExtractionFinishedEvent(UUID extractionId, ExtractionTask task, Extraction.Status status) {
}
