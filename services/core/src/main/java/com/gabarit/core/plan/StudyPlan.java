package com.gabarit.core.plan;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Plano de estudos de um concurso da carteira (um por concurso). As sessões ficam em {@link PlanSession}. */
@Entity
@Table(name = "study_plan")
class StudyPlan {

	// UUID v7: ordenado pelo tempo (não espalha inserções pelo índice) e não dá para adivinhar
	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	@Column(nullable = false, updatable = false)
	private String userId;

	@Column(nullable = false, updatable = false)
	private UUID studyTargetId;

	@Column(nullable = false)
	private LocalDate endDate;

	// minutos por dia da semana, segunda primeiro
	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(nullable = false)
	private int[] dailyMinutes;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant updatedAt;

	protected StudyPlan() {
		// JPA
	}

	StudyPlan(String userId, UUID studyTargetId, Instant now) {
		this.userId = userId;
		this.studyTargetId = studyTargetId;
		this.createdAt = now;
	}

	void update(LocalDate endDate, int[] dailyMinutes, Instant now) {
		this.endDate = endDate;
		this.dailyMinutes = dailyMinutes.clone();
		this.updatedAt = now;
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

	LocalDate getEndDate() {
		return endDate;
	}

	int[] getDailyMinutes() {
		return dailyMinutes.clone();
	}

	Instant getUpdatedAt() {
		return updatedAt;
	}

}
