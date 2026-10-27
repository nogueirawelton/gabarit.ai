package com.gabarit.core.exam;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

// package-private: outros pacotes acessam pelo serviço do pacote, nunca pelo repositório
interface CatalogPositionRepository extends JpaRepository<CatalogPosition, Long> {

	// o catálogo inteiro de uma fonte cabe em memória (~250 cargos): uma query por evento
	List<CatalogPosition> findBySource(String source);

}
