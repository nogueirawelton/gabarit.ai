package com.gabarit.core.plan;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface StudyPlanRepository extends JpaRepository<StudyPlan, UUID> {

	Optional<StudyPlan> findByStudyTargetIdAndUserId(UUID studyTargetId, String userId);

	List<StudyPlan> findByUserId(String userId);

}
