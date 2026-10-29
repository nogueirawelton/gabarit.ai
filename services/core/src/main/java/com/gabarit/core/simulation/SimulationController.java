package com.gabarit.core.simulation;

import com.gabarit.core.simulation.SimulationService.SimulationSummary;
import com.gabarit.core.simulation.SimulationService.SimulationView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Simulados de um concurso da carteira. Começar: {@code POST .../simulations} com a prova
 * relacionada (já lida); responder: {@code PUT /me/simulations/{id}/answers/{questionId}} a cada
 * marcação; entregar: {@code POST /me/simulations/{id}/finish}.
 */
@RestController
class SimulationController {

	/** {@code timeLimitMinutes} nulo = sem limite de tempo. */
	record StartRequest(@NotNull UUID paperId,
			@Min(10) @Max(SimulationService.MAX_TIME_LIMIT_MINUTES) Integer timeLimitMinutes) {
	}

	/** Letra da alternativa, ou C/E em certo/errado; nulo desmarca. */
	record AnswerRequest(@Size(max = 2) String answer) {
	}

	private final SimulationService service;

	SimulationController(SimulationService service) {
		this.service = service;
	}

	@PostMapping("/me/study-targets/{targetId}/simulations")
	@ResponseStatus(HttpStatus.CREATED)
	SimulationView start(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID targetId,
			@Valid @RequestBody StartRequest request) {
		return service.start(jwt.getSubject(), targetId, request.paperId(), request.timeLimitMinutes());
	}

	@GetMapping("/me/study-targets/{targetId}/simulations")
	List<SimulationSummary> list(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID targetId) {
		return service.list(jwt.getSubject(), targetId);
	}

	@GetMapping("/me/simulations/{id}")
	SimulationView get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
		return service.get(jwt.getSubject(), id);
	}

	@PutMapping("/me/simulations/{id}/answers/{questionId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void answer(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @PathVariable UUID questionId,
			@Valid @RequestBody AnswerRequest request) {
		service.answer(jwt.getSubject(), id, questionId, request.answer());
	}

	@PostMapping("/me/simulations/{id}/finish")
	SimulationView finish(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
		return service.finish(jwt.getSubject(), id);
	}

}
