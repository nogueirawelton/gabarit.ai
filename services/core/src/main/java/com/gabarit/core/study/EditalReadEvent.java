package com.gabarit.core.study;

import java.util.UUID;

/**
 * Evento interno (Spring, não RabbitMQ): terminou a leitura do edital de um concurso da carteira de
 * alguém. Publicado dentro da transação que gravou o resultado da leitura: quem ouve com
 * {@code @EventListener} grava junto. Leva o título para o ouvinte não depender do pacote study.
 *
 * @param ok leitura pronta; {@code false} = falhou
 */
public record EditalReadEvent(String userId, UUID studyTargetId, String title, boolean ok) {
}
