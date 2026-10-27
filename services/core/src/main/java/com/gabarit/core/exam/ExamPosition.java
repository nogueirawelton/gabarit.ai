package com.gabarit.core.exam;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Cargo de um concurso. Só existe dentro do {@link Exam}, que é quem cria, ativa e inativa.
 * Nome cru da fonte: padronizar entre bancas é outra etapa.
 */
@Entity
@Table(name = "exam_position")
public class ExamPosition {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "exam_id", nullable = false, updatable = false)
	private Exam exam;

	@Column(nullable = false, updatable = false)
	private String name;

	@Column(nullable = false)
	private boolean active;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant updatedAt;

	protected ExamPosition() {
		// JPA
	}

	ExamPosition(Exam exam, String name) {
		this.exam = exam;
		this.name = name;
		this.active = true;
	}

	void activate() {
		active = true;
	}

	void deactivate() {
		active = false;
	}

	@PrePersist
	void onCreate() {
		createdAt = Instant.now();
		updatedAt = createdAt;
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public Exam getExam() {
		return exam;
	}

	public String getName() {
		return name;
	}

	public boolean isActive() {
		return active;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}
