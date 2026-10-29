package com.gabarit.core.exam;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

// package-private: outros pacotes acessam concursos pelo serviço do pacote, nunca pelo repositório
interface ExamRepository extends JpaRepository<Exam, UUID>, JpaSpecificationExecutor<Exam> {

	// já traz os cargos: o upsert sempre mexe neles, e assim é uma query só
	@EntityGraph(attributePaths = "positions")
	Optional<Exam> findBySourceAndExternalId(String source, String externalId);

	// detalhe do concurso: com os cargos, numa query só
	@EntityGraph(attributePaths = "positions")
	Optional<Exam> findWithPositionsById(UUID id);

}
