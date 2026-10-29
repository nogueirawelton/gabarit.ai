package com.gabarit.core.simulation;

import com.gabarit.core.simulation.GenerationService.GenerationState;
import com.gabarit.core.simulation.GenerationService.TopicPractice;
import com.gabarit.core.simulation.TopicService.PaperTopics;
import com.gabarit.core.simulation.TopicService.TopicsView;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Questões das provas relacionadas ligadas aos tópicos do conteúdo do cargo. */
@RestController
class TopicController {

	private final TopicService service;

	private final GenerationService generation;

	TopicController(TopicService service, GenerationService generation) {
		this.service = service;
		this.generation = generation;
	}

	/** O que mais cai (questões por tópico) e a ligação de cada prova. */
	@GetMapping("/me/study-targets/{targetId}/topics")
	TopicsView overview(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID targetId) {
		return service.overview(jwt.getSubject(), targetId);
	}

	/** Cada tópico do conteúdo com as questões para praticar (reais e geradas) e a geração. */
	@GetMapping("/me/study-targets/{targetId}/topic-practice")
	List<TopicPractice> topicPractice(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID targetId) {
		return generation.overview(jwt.getSubject(), targetId);
	}

	/** Gera questões inéditas para o tópico (ordinais a partir de 0), no estilo da banca. */
	@PostMapping("/me/study-targets/{targetId}/topics/{subject}/{topic}/generate")
	GenerationState generate(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID targetId, @PathVariable int subject,
			@PathVariable int topic) {
		return generation.generate(jwt.getSubject(), targetId, subject, topic);
	}

	/** Prática (simulado sem tempo) do tópico; 201 com o simulado. */
	@PostMapping("/me/study-targets/{targetId}/topics/{subject}/{topic}/practice")
	@ResponseStatus(HttpStatus.CREATED)
	SimulationService.SimulationView practice(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID targetId,
			@PathVariable int subject, @PathVariable int topic) {
		return generation.practice(jwt.getSubject(), targetId, subject, topic);
	}

	/** Pede a ligação das questões da prova aos tópicos (ou de novo). */
	@PostMapping("/me/study-targets/{targetId}/papers/{paperId}/topics")
	PaperTopics classify(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID targetId, @PathVariable UUID paperId) {
		return service.classify(jwt.getSubject(), targetId, paperId);
	}

}
