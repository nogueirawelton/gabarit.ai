package com.gabarit.core.exam;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

// package-private: só leitura em lote; cargo é criado e alterado pelo Exam
interface ExamPositionRepository extends JpaRepository<ExamPosition, UUID> {

	/** Cargos ativos de vários concursos de uma vez (uma página da listagem): uma query, não uma por concurso. */
	List<ExamPosition> findByExamIdInAndActiveTrue(Collection<UUID> examIds);

}
