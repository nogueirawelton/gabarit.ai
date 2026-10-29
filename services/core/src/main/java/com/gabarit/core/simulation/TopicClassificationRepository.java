package com.gabarit.core.simulation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface TopicClassificationRepository extends JpaRepository<TopicClassification, UUID> {

	Optional<TopicClassification> findByStudyPaperId(UUID studyPaperId);

	List<TopicClassification> findByStudyTargetIdAndUserId(UUID studyTargetId, String userId);

	List<TopicClassification> findByExtractionId(UUID extractionId);

}
