package com.gabarit.core.shared;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Página da API, com formato fixo. O {@code Page} do Spring serializado direto gera um JSON que
 * muda entre versões (o Spring Data avisa no log).
 */
public record PageResponse<T>(List<T> items, int page, int size, long totalElements, int totalPages) {

	public static <T> PageResponse<T> of(Page<T> page) {
		return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(),
				page.getTotalPages());
	}

	public static <T> PageResponse<T> empty(int page, int size) {
		return new PageResponse<>(List.of(), page, size, 0, 0);
	}

}
