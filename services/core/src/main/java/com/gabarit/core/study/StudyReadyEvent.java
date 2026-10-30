package com.gabarit.core.study;

import java.util.UUID;

/**
 * Evento interno (Spring, não RabbitMQ): terminou uma leitura de um concurso da carteira de alguém,
 * além da do edital ({@link EditalReadEvent}). Publicado na transação que gravou o resultado: quem
 * ouve com {@code @EventListener} grava junto. Leva o título para o ouvinte não depender do pacote
 * study.
 *
 * @param ok pronta; {@code false} = falhou
 */
public record StudyReadyEvent(String userId, UUID studyTargetId, String title, What what, boolean ok) {

	public enum What {

		/** Conteúdo programático do cargo. */
		SYLLABUS,
		/** Questões de uma prova relacionada. */
		PAPER,
		/** Questões geradas (o simulado inédito ou a prática de um tópico). */
		QUESTIONS

	}

}
