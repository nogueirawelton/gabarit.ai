package com.gabarit.core.simulation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

interface SyllabusTopicRepository extends JpaRepository<SyllabusTopic, UUID> {

	@Query("""
			SELECT t FROM SyllabusTopic t JOIN FETCH t.subject s
			WHERE s.extractionId = :extractionId ORDER BY s.ordinal, t.ordinal""")
	List<SyllabusTopic> findBySyllabus(UUID extractionId);

}
