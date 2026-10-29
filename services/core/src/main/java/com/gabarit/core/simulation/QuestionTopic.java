package com.gabarit.core.simulation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

/** Questão ligada a um tópico do conteúdo de um cargo (pela leitura QUESTION_TOPICS). */
@Entity
@Table(name = "question_topic")
class QuestionTopic {

	// UUID v7: ordenado pelo tempo (não espalha inserções pelo índice) e não dá para adivinhar
	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	@Column(nullable = false, updatable = false)
	private UUID questionId;

	@Column(nullable = false, updatable = false)
	private UUID topicId;

	protected QuestionTopic() {
		// JPA
	}

	QuestionTopic(UUID questionId, UUID topicId) {
		this.questionId = questionId;
		this.topicId = topicId;
	}

}
