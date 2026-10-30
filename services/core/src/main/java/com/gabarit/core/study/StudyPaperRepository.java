package com.gabarit.core.study;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

// package-private: outros pacotes usam o StudyDocumentService
interface StudyPaperRepository extends JpaRepository<StudyPaper, UUID> {

	List<StudyPaper> findByStudyTargetIdOrderByCreatedAtAscIdAsc(UUID studyTargetId);

	Optional<StudyPaper> findByIdAndStudyTargetIdAndUserId(UUID id, UUID studyTargetId, String userId);

	long countByStudyTargetId(UUID studyTargetId);

	List<StudyPaper> findByExtractionId(UUID extractionId);

}
