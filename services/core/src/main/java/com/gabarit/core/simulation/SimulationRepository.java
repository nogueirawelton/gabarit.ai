package com.gabarit.core.simulation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SimulationRepository extends JpaRepository<Simulation, UUID> {

	Optional<Simulation> findByIdAndUserId(UUID id, String userId);

	List<Simulation> findByStudyTargetIdAndUserIdOrderByStartedAtDesc(UUID studyTargetId, String userId);

}
