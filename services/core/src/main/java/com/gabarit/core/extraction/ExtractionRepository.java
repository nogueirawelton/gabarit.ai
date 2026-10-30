package com.gabarit.core.extraction;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// package-private: outros pacotes usam o ExtractionService
interface ExtractionRepository extends JpaRepository<Extraction, UUID> {

	Optional<Extraction> findByTaskAndInputsHashAndPromptVersion(ExtractionTask task, String inputsHash,
			String promptVersion);

	long countByRequestedByAndCreatedAtAfter(String requestedBy, Instant after);

	List<Extraction> findByStatusAndRequestedAtBefore(Extraction.Status status, Instant before);

}
