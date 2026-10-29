package com.gabarit.core.simulation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * Pedido de ligar as questões de uma prova relacionada aos tópicos do conteúdo do cargo. Um por
 * prova: pedir de novo (outra leitura da prova ou do conteúdo) substitui.
 */
@Entity
@Table(name = "topic_classification")
class TopicClassification {

	// UUID v7: ordenado pelo tempo (não espalha inserções pelo índice) e não dá para adivinhar
	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	@Column(nullable = false, updatable = false)
	private String userId;

	@Column(nullable = false, updatable = false)
	private UUID studyTargetId;

	@Column(nullable = false, updatable = false)
	private UUID studyPaperId;

	@Column(nullable = false)
	private UUID examReadingId;

	@Column(nullable = false)
	private UUID syllabusReadingId;

	@Column(nullable = false)
	private UUID extractionId;

	@Column(nullable = false)
	private Instant requestedAt;

	protected TopicClassification() {
		// JPA
	}

	TopicClassification(String userId, UUID studyTargetId, UUID studyPaperId) {
		this.userId = userId;
		this.studyTargetId = studyTargetId;
		this.studyPaperId = studyPaperId;
	}

	void request(UUID examReadingId, UUID syllabusReadingId, UUID extractionId, Instant now) {
		this.examReadingId = examReadingId;
		this.syllabusReadingId = syllabusReadingId;
		this.extractionId = extractionId;
		this.requestedAt = now;
	}

	UUID getStudyPaperId() {
		return studyPaperId;
	}

	UUID getExamReadingId() {
		return examReadingId;
	}

	UUID getSyllabusReadingId() {
		return syllabusReadingId;
	}

	UUID getExtractionId() {
		return extractionId;
	}

}
