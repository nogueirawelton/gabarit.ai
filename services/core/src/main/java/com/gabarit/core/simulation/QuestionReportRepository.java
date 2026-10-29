package com.gabarit.core.simulation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface QuestionReportRepository extends JpaRepository<QuestionReport, UUID> {

	Optional<QuestionReport> findByQuestionIdAndUserId(UUID questionId, String userId);

	/** Das questões dadas, as que a pessoa reportou. */
	@Query("SELECT r.questionId FROM QuestionReport r WHERE r.userId = :userId AND r.questionId IN :questions")
	List<UUID> reportedBy(String userId, Collection<UUID> questions);

}
