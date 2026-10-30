package com.gabarit.core.plan;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface StudyPlanRepository extends JpaRepository<StudyPlan, UUID> {

	Optional<StudyPlan> findByStudyTargetIdAndUserId(UUID studyTargetId, String userId);

	List<StudyPlan> findByUserId(String userId);

	/** Planos com prova depois de hoje e sessão de antes de hoje não feita (atrasados). */
	@Query("""
			SELECT p FROM StudyPlan p WHERE p.endDate > :today AND EXISTS (
			  SELECT 1 FROM PlanSession s WHERE s.planId = p.id AND s.date < :today AND s.doneAt IS NULL)""")
	List<StudyPlan> findLate(LocalDate today);

}
