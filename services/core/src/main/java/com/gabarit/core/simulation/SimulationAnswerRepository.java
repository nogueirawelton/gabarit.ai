package com.gabarit.core.simulation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SimulationAnswerRepository extends JpaRepository<SimulationAnswer, UUID> {

	List<SimulationAnswer> findBySimulationId(UUID simulationId);

	List<SimulationAnswer> findBySimulationIdIn(Collection<UUID> simulationIds);

	Optional<SimulationAnswer> findBySimulationIdAndQuestionId(UUID simulationId, UUID questionId);

}
