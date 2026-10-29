package com.gabarit.core.plan;

import com.gabarit.core.plan.PlanService.AgendaSession;
import com.gabarit.core.plan.PlanService.PlanView;
import com.gabarit.core.simulation.SimulationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Plano de estudos de um concurso e a agenda das sessões de todos os concursos. */
@RestController
class PlanController {

	/** {@code dailyMinutes}: minutos por dia da semana, segunda primeiro. */
	record PlanRequest(@NotNull LocalDate endDate, @NotNull @Size(min = 7, max = 7) List<@NotNull Integer> dailyMinutes) {
	}

	record SessionRequest(boolean done) {
	}

	private final PlanService service;

	PlanController(PlanService service) {
		this.service = service;
	}

	/** 200 com o plano, ou 204 se o concurso ainda não tem plano. */
	@GetMapping("/me/study-targets/{targetId}/plan")
	ResponseEntity<PlanView> get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID targetId) {
		return service.get(jwt.getSubject(), targetId).map(ResponseEntity::ok).orElse(ResponseEntity.noContent().build());
	}

	/** Monta ou refaz o plano (as sessões feitas ficam). */
	@PutMapping("/me/study-targets/{targetId}/plan")
	PlanView save(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID targetId, @Valid @RequestBody PlanRequest request) {
		return service.save(jwt.getSubject(), targetId, request.endDate(),
				request.dailyMinutes().stream().mapToInt(Integer::intValue).toArray());
	}

	@PutMapping("/me/plan-sessions/{sessionId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void done(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID sessionId, @RequestBody SessionRequest request) {
		service.done(jwt.getSubject(), sessionId, request.done());
	}

	/** Prática (simulado sem tempo) com as questões dos tópicos da sessão; 201 com o simulado. */
	@PostMapping("/me/plan-sessions/{sessionId}/practice")
	@ResponseStatus(HttpStatus.CREATED)
	SimulationService.SimulationView practice(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID sessionId) {
		return service.practice(jwt.getSubject(), sessionId);
	}

	/** Sessões de todos os concursos no período (datas inclusivas, até 62 dias). */
	@GetMapping("/me/plan-sessions")
	List<AgendaSession> agenda(@AuthenticationPrincipal Jwt jwt,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		return service.agenda(jwt.getSubject(), from, to);
	}

}
