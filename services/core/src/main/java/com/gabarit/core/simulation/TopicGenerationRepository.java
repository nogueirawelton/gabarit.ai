package com.gabarit.core.simulation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface TopicGenerationRepository extends JpaRepository<TopicGeneration, UUID> {

	Optional<TopicGeneration> findByStudyTargetIdAndTopicId(UUID studyTargetId, UUID topicId);

	List<TopicGeneration> findByStudyTargetIdAndUserId(UUID studyTargetId, String userId);

	List<TopicGeneration> findByExtractionId(UUID extractionId);

}
