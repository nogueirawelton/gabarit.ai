package com.gabarit.core.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * Aviso para um usuário: concurso novo ({@code NEW_EXAM}) ou leitura do edital de um concurso da
 * carteira ({@code EDITAL_*}). Guarda só os ids: o concurso é do pacote exam e o usuário
 * vem do Keycloak (sub), lidos pelos serviços de cada um.
 */
@Entity
@Table(name = "notification")
public class Notification {

	public enum Type {

		/** Concurso novo que bate com o perfil (vai no resumo por e-mail). */
		NEW_EXAM,
		/** A leitura do edital de um concurso da carteira ficou pronta. */
		EDITAL_READ,
		/** A leitura do edital falhou. */
		EDITAL_FAILED

	}

	// UUID v7: ordenado pelo tempo (não espalha inserções pelo índice) e não dá para adivinhar
	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	@Column(nullable = false, updatable = false)
	private String userId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false)
	private Type type;

	// NEW_EXAM
	@Column(updatable = false)
	private UUID examId;

	// EDITAL_*: o concurso da carteira e o título dele no momento do aviso
	@Column(updatable = false)
	private UUID studyTargetId;

	@Column(updatable = false)
	private String title;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	private Instant readAt;

	private Instant emailedAt;

	protected Notification() {
		// JPA
	}

	Notification(String userId, UUID examId, Instant createdAt) {
		this.userId = userId;
		this.type = Type.NEW_EXAM;
		this.examId = examId;
		this.createdAt = createdAt;
	}

	static Notification editalReading(String userId, UUID studyTargetId, String title, boolean ok, Instant createdAt) {
		Notification notification = new Notification();
		notification.userId = userId;
		notification.type = ok ? Type.EDITAL_READ : Type.EDITAL_FAILED;
		notification.studyTargetId = studyTargetId;
		notification.title = title;
		notification.createdAt = createdAt;
		return notification;
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

	public Type getType() {
		return type;
	}

	public UUID getExamId() {
		return examId;
	}

	public UUID getStudyTargetId() {
		return studyTargetId;
	}

	public String getTitle() {
		return title;
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
