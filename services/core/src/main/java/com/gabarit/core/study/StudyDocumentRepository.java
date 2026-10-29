package com.gabarit.core.study;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// package-private: outros pacotes usam o StudyDocumentService
interface StudyDocumentRepository extends JpaRepository<StudyDocument, UUID> {

	List<StudyDocument> findByStudyTargetIdOrderByCreatedAtAscIdAsc(UUID studyTargetId);

	Optional<StudyDocument> findByPaperIdAndKind(UUID paperId, StudyDocument.Kind kind);

	Optional<StudyDocument> findByIdAndStudyTargetIdAndUserId(UUID id, UUID studyTargetId, String userId);

	long countByStudyTargetIdAndStatusNot(UUID studyTargetId, StudyDocument.Status status);

	long countByUserIdAndCreatedAtAfter(String userId, Instant since);

	/** Envios que nunca foram confirmados (o arquivo, se chegou, o MinIO apaga sozinho em 1 dia). */
	@Modifying
	@Query("delete from StudyDocument d where d.status = com.gabarit.core.study.StudyDocument.Status.UPLOADING"
			+ " and d.createdAt < :before")
	int deleteStaleUploads(Instant before);

}
