package com.gabarit.core.exam;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

// package-private: outros pacotes acessam concursos pelo serviço do pacote, nunca pelo repositório
interface ExamRepository extends JpaRepository<Exam, Long> {

	// já traz os cargos: o upsert sempre mexe neles, e assim é uma query só
	@EntityGraph(attributePaths = "positions")
	Optional<Exam> findBySourceAndExternalId(String source, String externalId);

}
