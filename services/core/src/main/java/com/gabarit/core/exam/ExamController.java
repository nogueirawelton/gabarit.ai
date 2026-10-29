package com.gabarit.core.exam;

import com.gabarit.core.shared.PageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;
import java.util.UUID;

/**
 * Concursos, para qualquer usuário logado. Os do perfil do usuário ("meus concursos") ficam em
 * {@code user}: o pacote exam não conhece usuário.
 */
@RestController
class ExamController {

	private final ExamService examService;

	ExamController(ExamService examService) {
		this.examService = examService;
	}

	/** Parâmetros fora do limite (ou status/escolaridade desconhecidos) → 400 em ProblemDetail. */
	@GetMapping("/exams")
	PageResponse<ExamSummary> list(@RequestParam(name = "uf", required = false) Set<String> ufs,
			@RequestParam(name = "status", required = false) Set<ExamStatus> statuses,
			@RequestParam(name = "educationLevel", required = false) Set<EducationLevel> educationLevels,
			@RequestParam(required = false) @Size(max = 100) String q,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(ExamService.MAX_PAGE_SIZE) int size) {
		return PageResponse.of(examService.search(new ExamFilter(ufs, statuses, educationLevels, q, null), page, size));
	}

	@GetMapping("/exams/{id}")
	ExamDetail get(@PathVariable UUID id) {
		return examService.get(id);
	}

}
