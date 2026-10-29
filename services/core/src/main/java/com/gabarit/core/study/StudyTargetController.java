package com.gabarit.core.study;

import com.gabarit.core.study.StudyTargetService.StudyTargetDetail;
import com.gabarit.core.study.StudyTargetService.StudyTargetSummary;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Carteira do usuário logado: concursos que ele estuda. */
@RestController
class StudyTargetController {

	/** Um dos dois: {@code examId} (concurso da lista) ou {@code title} (concurso cadastrado à mão). */
	record AddRequest(UUID examId, @Size(max = 200) String title) {

		@AssertTrue(message = "informe examId ou title, não os dois")
		boolean isExactlyOne() {
			return (examId != null) != (title != null && !title.isBlank());
		}

	}

	/** Estado completo dos campos editáveis; vazio apaga. */
	record UpdateRequest(@NotBlank @Size(max = 200) String title, @Size(max = 200) String organ,
			@Size(max = 100) String board, @Min(1950) @Max(2100) Integer year, @Size(max = 200) String positionName,
			LocalDate examDate) {
	}

	private final StudyTargetService service;

	StudyTargetController(StudyTargetService service) {
		this.service = service;
	}

	@GetMapping("/me/study-targets")
	List<StudyTargetSummary> list(@AuthenticationPrincipal Jwt jwt) {
		return service.list(jwt.getSubject());
	}

	/** 201 com o item novo; 200 se o concurso da lista já estava na carteira. */
	@PostMapping("/me/study-targets")
	ResponseEntity<StudyTargetDetail> add(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AddRequest request) {
		if (request.examId() != null) {
			var added = service.addExam(jwt.getSubject(), request.examId());
			return added.created() ? created(added.target()) : ResponseEntity.ok(added.target());
		}
		return created(service.addCustom(jwt.getSubject(), request.title()));
	}

	@GetMapping("/me/study-targets/{id}")
	StudyTargetDetail get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
		return service.get(jwt.getSubject(), id);
	}

	@PutMapping("/me/study-targets/{id}")
	StudyTargetDetail update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
			@Valid @RequestBody UpdateRequest request) {
		return service.update(jwt.getSubject(), id, new StudyTargetService.Details(request.title(), request.organ(),
				request.board(), request.year(), request.positionName(), request.examDate()));
	}

	@DeleteMapping("/me/study-targets/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void remove(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
		service.remove(jwt.getSubject(), id);
	}

	private static ResponseEntity<StudyTargetDetail> created(StudyTargetDetail target) {
		return ResponseEntity.created(URI.create("/me/study-targets/" + target.id())).body(target);
	}

}
