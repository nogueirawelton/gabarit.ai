package com.gabarit.core.study;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

// package-private: outros pacotes usam o StudyTargetService
interface StudyTargetRepository extends JpaRepository<StudyTarget, UUID> {

	List<StudyTarget> findByUserIdOrderByCreatedAtDescIdDesc(String userId);

	Optional<StudyTarget> findByIdAndUserId(UUID id, String userId);

	Optional<StudyTarget> findByUserIdAndExamId(String userId, UUID examId);

	long countByUserId(String userId);

}
