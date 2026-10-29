package com.gabarit.core.document;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Um PDF guardado no MinIO, identificado pelo SHA-256 do conteúdo (um por conteúdo). */
@Entity
@Table(name = "document_blob")
class DocumentBlob {

	@Id
	private String sha256;

	@Column(nullable = false)
	private String storageKey;

	@Column(nullable = false)
	private long sizeBytes;

	@Column(nullable = false)
	private int pages;

	@Column(nullable = false)
	private Instant createdAt;

	protected DocumentBlob() {
		// JPA
	}

	String getStorageKey() {
		return storageKey;
	}

}
