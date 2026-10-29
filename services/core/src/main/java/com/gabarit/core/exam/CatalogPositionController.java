package com.gabarit.core.exam;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Catálogo de cargos (para o usuário escolher os cargos de interesse). */
@RestController
class CatalogPositionController {

	record CatalogPositionResponse(UUID id, String slug, String name) {

		static CatalogPositionResponse of(CatalogPosition position) {
			return new CatalogPositionResponse(position.getId(), position.getSlug(), position.getName());
		}

	}

	private final CatalogPositionService catalogService;

	CatalogPositionController(CatalogPositionService catalogService) {
		this.catalogService = catalogService;
	}

	@GetMapping("/catalog/positions")
	List<CatalogPositionResponse> list(@RequestParam(defaultValue = "pciconcursos") String source) {
		return catalogService.list(source).stream().map(CatalogPositionResponse::of).toList();
	}

}
