package com.gabarit.core.exam;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Cargo do catálogo de uma fonte (ex.: "Enfermeiro"), que agrupa os nomes crus dos concursos
 * ({@link ExamPosition#getCatalogSlugs()}). Identidade: (source, slug).
 */
@Entity
@Table(name = "position_catalog")
public class CatalogPosition {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, updatable = false)
	private String source;

	@Column(nullable = false, updatable = false)
	private String slug;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false)
	private Instant sourceUpdatedAt;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant updatedAt;

	protected CatalogPosition() {
		// JPA
	}

	CatalogPosition(String source, String slug, String name, Instant occurredAt) {
		this.source = source;
		this.slug = slug;
		this.name = name;
		this.sourceUpdatedAt = occurredAt;
	}

	/** @return false se o evento é mais antigo (ou o mesmo) que o último aplicado */
	boolean apply(String name, Instant occurredAt) {
		if (!occurredAt.isAfter(sourceUpdatedAt)) {
			return false;
		}
		this.name = name;
		this.sourceUpdatedAt = occurredAt;
		return true;
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

	public String getSource() {
		return source;
	}

	public String getSlug() {
		return slug;
	}

	public String getName() {
		return name;
	}

	public Instant getSourceUpdatedAt() {
		return sourceUpdatedAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}
