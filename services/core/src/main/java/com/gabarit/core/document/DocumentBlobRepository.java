package com.gabarit.core.document;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;

// package-private: outros pacotes usam o DocumentService
interface DocumentBlobRepository extends JpaRepository<DocumentBlob, String> {

	/**
	 * Registra o blob; se outra pessoa enviou o mesmo arquivo ao mesmo tempo e já registrou, não faz
	 * nada (o conteúdo é o mesmo, por definição).
	 */
	@Modifying
	@Query(value = """
			INSERT INTO document_blob (sha256, storage_key, size_bytes, pages, created_at)
			VALUES (:sha256, :storageKey, :sizeBytes, :pages, :createdAt)
			ON CONFLICT (sha256) DO NOTHING
			""", nativeQuery = true)
	void insertIfAbsent(String sha256, String storageKey, long sizeBytes, int pages, Instant createdAt);

}
