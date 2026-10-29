package com.gabarit.core.simulation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/** Pedido de questões geradas para um tópico do conteúdo do cargo (um por tópico; pedir de novo substitui). */
@Entity
@Table(name = "topic_generation")
class TopicGeneration {

	// UUID v7: ordenado pelo tempo (não espalha inserções pelo índice) e não dá para adivinhar
	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	@Column(nullable = false, updatable = false)
	private String userId;

	@Column(nullable = false, updatable = false)
	private UUID studyTargetId;

	@Column(nullable = false, updatable = false)
	private UUID topicId;

	@Column(nullable = false)
	private UUID extractionId;

	@Column(nullable = false)
	private Instant requestedAt;

	protected TopicGeneration() {
		// JPA
	}

	TopicGeneration(String userId, UUID studyTargetId, UUID topicId) {
		this.userId = userId;
		this.studyTargetId = studyTargetId;
		this.topicId = topicId;
	}

	void request(UUID extractionId, Instant now) {
		this.extractionId = extractionId;
		this.requestedAt = now;
	}

	UUID getTopicId() {
		return topicId;
	}

	UUID getExtractionId() {
		return extractionId;
	}

}
