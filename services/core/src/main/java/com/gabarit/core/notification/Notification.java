package com.gabarit.core.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * Aviso de concurso novo para um usuário. Guarda só os ids: o concurso é do pacote exam e o usuário
 * vem do Keycloak (sub), lidos pelos serviços de cada um.
 */
@Entity
@Table(name = "notification")
public class Notification {

	// UUID v7: ordenado pelo tempo (não espalha inserções pelo índice) e não dá para adivinhar
	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	@Column(nullable = false, updatable = false)
	private String userId;

	@Column(nullable = false, updatable = false)
	private UUID examId;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	private Instant readAt;

	private Instant emailedAt;

	protected Notification() {
		// JPA
	}

	Notification(String userId, UUID examId, Instant createdAt) {
		this.userId = userId;
		this.examId = examId;
		this.createdAt = createdAt;
	}

	void markRead(Instant at) {
		if (readAt == null) {
			readAt = at;
		}
	}

	void markEmailed(Instant at) {
		emailedAt = at;
	}

	public UUID getId() {
		return id;
	}

	public String getUserId() {
		return userId;
	}

	public UUID getExamId() {
		return examId;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getReadAt() {
		return readAt;
	}

	public Instant getEmailedAt() {
		return emailedAt;
	}

}
