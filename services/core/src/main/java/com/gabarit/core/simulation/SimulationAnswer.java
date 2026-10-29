package com.gabarit.core.simulation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/** Resposta marcada numa questão do simulado (uma por questão; apagar a marcação apaga a linha). */
@Entity
@Table(name = "simulation_answer")
class SimulationAnswer {

	// UUID v7: ordenado pelo tempo (não espalha inserções pelo índice) e não dá para adivinhar
	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	@Column(nullable = false, updatable = false)
	private UUID simulationId;

	@Column(nullable = false, updatable = false)
	private UUID questionId;

	@Column(nullable = false)
	private String answer;

	@Column(nullable = false)
	private Instant answeredAt;

	protected SimulationAnswer() {
		// JPA
	}

	SimulationAnswer(UUID simulationId, UUID questionId, String answer, Instant answeredAt) {
		this.simulationId = simulationId;
		this.questionId = questionId;
		change(answer, answeredAt);
	}

	void change(String answer, Instant answeredAt) {
		this.answer = answer;
		this.answeredAt = answeredAt;
	}

	UUID getSimulationId() {
		return simulationId;
	}

	UUID getQuestionId() {
		return questionId;
	}

	String getAnswer() {
		return answer;
	}

}
