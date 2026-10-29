package com.gabarit.core.study;

import com.gabarit.core.study.StudyDocumentService.DocumentView;
import com.gabarit.core.study.StudyDocumentService.Materials;
import com.gabarit.core.study.StudyDocumentService.PaperView;
import com.gabarit.core.study.StudyDocumentService.UploadStarted;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
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

import java.util.UUID;

/**
 * Materiais de um concurso da carteira: editais e provas relacionadas (prova + gabarito). Envio de
 * arquivo: {@code POST .../documents} (devolve a URL), PUT do PDF direto no MinIO,
 * {@code POST .../documents/{id}/complete} (o core confere).
 */
@RestController
class StudyDocumentController {

	record PaperRequest(@Min(1950) @Max(2100) Integer year, @Size(max = 30) String variant,
			boolean answersIncluded) {
	}

	/** Edital do concurso-alvo: sem {@code paperId}. Prova, gabarito e edital da prova: o espaço da prova relacionada. */
	record StartRequest(@NotNull StudyDocument.Kind kind, UUID paperId, @NotBlank @Size(max = 255) String name,
			@NotNull @Positive Long size) {
	}

	/** Um dos cargos da leitura do edital, exatamente como ela traz. */
	record PositionRequest(@NotBlank @Size(max = 300) String name, @Size(max = 300) String specialty) {
	}

	private final StudyDocumentService service;

	StudyDocumentController(StudyDocumentService service) {
		this.service = service;
	}

	@GetMapping("/me/study-targets/{targetId}/materials")
	Materials materials(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID targetId) {
		return service.materials(jwt.getSubject(), targetId);
	}

	@PostMapping("/me/study-targets/{targetId}/papers")
	@ResponseStatus(HttpStatus.CREATED)
	PaperView addPaper(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID targetId,
			@Valid @RequestBody PaperRequest request) {
		return service.addPaper(jwt.getSubject(), targetId, request.year(), request.variant(),
				request.answersIncluded());
	}

	@DeleteMapping("/me/study-targets/{targetId}/papers/{paperId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void removePaper(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID targetId, @PathVariable UUID paperId) {
		service.removePaper(jwt.getSubject(), targetId, paperId);
	}

	@PostMapping("/me/study-targets/{targetId}/documents")
	@ResponseStatus(HttpStatus.CREATED)
	UploadStarted start(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID targetId,
			@Valid @RequestBody StartRequest request) {
		return service.start(jwt.getSubject(), targetId, request.kind(), request.paperId(), request.name(),
				request.size());
	}

	/** 200 com o arquivo: {@code READY} ou {@code REJECTED} com o motivo. */
	@PostMapping("/me/study-targets/{targetId}/documents/{documentId}/complete")
	DocumentView complete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID targetId,
			@PathVariable UUID documentId) {
		return service.complete(jwt.getSubject(), targetId, documentId);
	}

	/** Pede (de novo) a leitura do edital do concurso; 200 com o arquivo e a leitura. */
	@PostMapping("/me/study-targets/{targetId}/documents/{documentId}/reading")
	DocumentView read(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID targetId, @PathVariable UUID documentId) {
		return service.read(jwt.getSubject(), targetId, documentId);
	}

	/** Escolhe o cargo entre os da leitura do edital; 200 com o arquivo. */
	@PutMapping("/me/study-targets/{targetId}/documents/{documentId}/position")
	DocumentView choosePosition(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID targetId,
			@PathVariable UUID documentId, @Valid @RequestBody PositionRequest request) {
		return service.choosePosition(jwt.getSubject(), targetId, documentId, request.name(), request.specialty());
	}

	/** Pede a leitura do conteúdo programático do cargo escolhido; 200 com o arquivo e a leitura. */
	@PostMapping("/me/study-targets/{targetId}/documents/{documentId}/syllabus")
	DocumentView readSyllabus(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID targetId,
			@PathVariable UUID documentId) {
		return service.readSyllabus(jwt.getSubject(), targetId, documentId);
	}

	@DeleteMapping("/me/study-targets/{targetId}/documents/{documentId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void remove(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID targetId, @PathVariable UUID documentId) {
		service.remove(jwt.getSubject(), targetId, documentId);
	}

}
