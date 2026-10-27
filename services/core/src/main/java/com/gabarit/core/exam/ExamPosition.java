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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Cargo de um concurso. Só existe dentro do {@link Exam}, que é quem cria, ativa e inativa.
 * Nome cru da fonte; {@code catalogSlugs} liga ao catálogo da fonte ({@link CatalogPosition}).
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

	// text[] no Postgres. Slugs da mesma source do concurso; sem FK de propósito (ver V2)
	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(nullable = false, columnDefinition = "text[]")
	private List<String> catalogSlugs = new ArrayList<>();

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant updatedAt;

	protected ExamPosition() {
		// JPA
	}

	ExamPosition(Exam exam, String name, Collection<String> catalogSlugs) {
		this.exam = exam;
		this.name = name;
		this.active = true;
		this.catalogSlugs = new ArrayList<>(catalogSlugs);
	}

	void activate(Collection<String> catalogSlugs) {
		active = true;
		// só troca se mudou: lista nova sempre marcaria a entidade como alterada
		if (!this.catalogSlugs.equals(List.copyOf(catalogSlugs))) {
			this.catalogSlugs = new ArrayList<>(catalogSlugs);
		}
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

	public List<String> getCatalogSlugs() {
		return Collections.unmodifiableList(catalogSlugs);
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}
