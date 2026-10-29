package com.gabarit.core.plan;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

interface PlanSessionRepository extends JpaRepository<PlanSession, UUID> {

	List<PlanSession> findByPlanIdOrderByDateAscPositionAsc(UUID planId);

	List<PlanSession> findByPlanIdInAndDateBetweenOrderByDateAscPositionAsc(Collection<UUID> planIds, LocalDate from,
			LocalDate to);

	/** Refazer o plano: as sessões não feitas saem (as feitas ficam). */
	@Modifying
	@Query("DELETE FROM PlanSession s WHERE s.planId = :planId AND s.doneAt IS NULL")
	void deleteUndone(UUID planId);

}
