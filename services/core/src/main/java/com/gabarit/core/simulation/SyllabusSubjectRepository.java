package com.gabarit.core.simulation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface SyllabusSubjectRepository extends JpaRepository<SyllabusSubject, UUID> {

	boolean existsByExtractionId(UUID extractionId);

}
