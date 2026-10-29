package com.gabarit.core.simulation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** Um simulado: as questões de uma prova lida, feitas como prova, com tempo opcional. */
@Entity
@Table(name = "simulation")
class Simulation {

	/** Folga depois do prazo para a última resposta chegar (rede lenta, aba em segundo plano). */
	static final Duration GRACE = Duration.ofMinutes(2);

	// UUID v7: ordenado pelo tempo (não espalha inserções pelo índice) e não dá para adivinhar
	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	@Column(nullable = false, updatable = false)
	private String userId;

	@Column(nullable = false, updatable = false)
	private UUID studyTargetId;

	// só o id: a prova relacionada é do pacote study (nula se ela foi removida)
	private UUID paperId;

	// a prova lida de onde vêm as questões; nula no simulado de prática (questões escolhidas, em
	// simulation_question)
	@Column(updatable = false)
	private UUID extractionId;

	@Column(nullable = false, updatable = false)
	private String title;

	@Column(nullable = false, updatable = false)
	private Instant startedAt;

	@Column(updatable = false)
	private Instant deadline;

	private Instant finishedAt;

	protected Simulation() {
		// JPA
	}

	Simulation(String userId, UUID studyTargetId, UUID paperId, UUID extractionId, String title, Instant startedAt,
			Duration timeLimit) {
		this.userId = userId;
		this.studyTargetId = studyTargetId;
		this.paperId = paperId;
		this.extractionId = extractionId;
		this.title = title;
		this.startedAt = startedAt;
		this.deadline = timeLimit == null ? null : startedAt.plus(timeLimit);
	}

	/** Entregue, ou o tempo acabou (com a folga): não aceita mais resposta e mostra o resultado. */
	boolean isFinished(Instant now) {
		return finishedAt != null || (deadline != null && now.isAfter(deadline.plus(GRACE)));
	}

	void finish(Instant now) {
		if (finishedAt == null) {
			// tempo esgotado: vale o prazo, não a hora em que a pessoa voltou
			finishedAt = deadline != null && now.isAfter(deadline) ? deadline : now;
		}
	}

	UUID getId() {
		return id;
	}

	String getUserId() {
		return userId;
	}

	UUID getStudyTargetId() {
		return studyTargetId;
	}

	UUID getPaperId() {
		return paperId;
	}

	UUID getExtractionId() {
		return extractionId;
	}

	String getTitle() {
		return title;
	}

	Instant getStartedAt() {
		return startedAt;
	}

	Instant getDeadline() {
		return deadline;
	}

	/** A entrega, ou o prazo, se o tempo acabou sem entregar. */
	Instant finishedAt(Instant now) {
		return finishedAt != null ? finishedAt : isFinished(now) ? deadline : null;
	}

}
