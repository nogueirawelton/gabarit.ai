package com.gabarit.core.simulation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/** Reporte de uma questão com problema por uma pessoa (um por questão e pessoa; reportar de novo atualiza). */
@Entity
@Table(name = "question_report")
class QuestionReport {

	enum Reason {

		/** O gabarito marca a alternativa errada. */
		WRONG_ANSWER,
		/** Enunciado ou alternativas cortados, ilegíveis ou com erro. */
		BROKEN_STATEMENT,
		/** Fora do conteúdo do cargo. */
		OUT_OF_SCOPE, OTHER

	}

	/** Reportes de pessoas diferentes que tiram a questão da prática de todos. */
	static final int REPORTS_TO_HIDE = 3;

	// UUID v7: ordenado pelo tempo (não espalha inserções pelo índice) e não dá para adivinhar
	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	@Column(nullable = false, updatable = false)
	private UUID questionId;

	@Column(nullable = false, updatable = false)
	private String userId;

	private UUID simulationId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Reason reason;

	private String comment;

	@Column(nullable = false)
	private Instant createdAt;

	protected QuestionReport() {
		// JPA
	}

	QuestionReport(UUID questionId, String userId) {
		this.questionId = questionId;
		this.userId = userId;
	}

	void report(UUID simulationId, Reason reason, String comment, Instant now) {
		this.simulationId = simulationId;
		this.reason = reason;
		this.comment = comment;
		this.createdAt = now;
	}

}
