package com.gabarit.core.user;

import com.gabarit.core.exam.ExamFilter;
import com.gabarit.core.exam.ExamService;
import com.gabarit.core.exam.ExamStatus;
import com.gabarit.core.exam.ExamSummary;
import com.gabarit.core.shared.PageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Concursos do perfil do usuário logado: com algum cargo que ele segue, nas UFs dele (nacionais
 * sempre) e com vaga nos níveis de escolaridade dele (sem escolaridade informada, sempre). Fica em {@code user} porque a dependência é user → exam; o contrário seria ciclo.
 */
@RestController
class MyExamsController {

	private final UserProfileService profileService;

	private final ExamService examService;

	MyExamsController(UserProfileService profileService, ExamService examService) {
		this.profileService = profileService;
		this.examService = examService;
	}

	/** Perfil sem cargo: lista vazia (a home convida a configurar), não todos os concursos. */
	@GetMapping("/me/exams")
	PageResponse<ExamSummary> list(@AuthenticationPrincipal Jwt jwt,
			@RequestParam(name = "status", required = false) Set<ExamStatus> statuses,
			@RequestParam(required = false) @Size(max = 100) String q,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(ExamService.MAX_PAGE_SIZE) int size) {
		UserProfileService.ProfileView profile = profileService.get(jwt.getSubject());
		if (profile.positions().isEmpty()) {
			return PageResponse.empty(page, size);
		}
		Map<String, Set<String>> slugsBySource = profile.catalogSlugsBySource();
		var filter = new ExamFilter(new HashSet<>(profile.ufs()), statuses, new HashSet<>(profile.educationLevels()), q,
				slugsBySource);
		return PageResponse.of(examService.search(filter, page, size));
	}

}
