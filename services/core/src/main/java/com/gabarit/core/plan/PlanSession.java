package com.gabarit.core.plan;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Uma sessão do plano num dia: estudar tópicos de uma disciplina, revisar ou fazer simulado. */
@Entity
@Table(name = "plan_session")
class PlanSession {

	enum Kind {

		STUDY, REVIEW, SIMULATION

	}

	private static final JsonMapper JSON = JsonMapper.builder().build();

	// UUID v7: ordenado pelo tempo (não espalha inserções pelo índice) e não dá para adivinhar
	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	@Column(nullable = false, updatable = false)
	private UUID planId;

	@Column(nullable = false, updatable = false)
	private LocalDate date;

	@Column(nullable = false, updatable = false)
	private int position;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false)
	private Kind kind;

	@Column(updatable = false)
	private Integer subjectOrdinal;

	@Column(updatable = false)
	private String subject;

	// JSON já serializado: a lista de textos dos tópicos
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, updatable = false)
	private String topics;

	@Column(nullable = false, updatable = false)
	private int minutes;

	private Instant doneAt;

	protected PlanSession() {
		// JPA
	}

	PlanSession(UUID planId, PlanBuilder.Draft draft) {
		this.planId = planId;
		this.date = draft.date();
		this.position = draft.position();
		this.kind = draft.kind();
		this.subjectOrdinal = draft.subjectOrdinal();
		this.subject = draft.subject();
		this.topics = JSON.writeValueAsString(draft.topics());
		this.minutes = draft.minutes();
	}

	void done(boolean done, Instant now) {
		this.doneAt = done ? (doneAt == null ? now : doneAt) : null;
	}

	UUID getId() {
		return id;
	}

	UUID getPlanId() {
		return planId;
	}

	LocalDate getDate() {
		return date;
	}

	Kind getKind() {
		return kind;
	}

	Integer getSubjectOrdinal() {
		return subjectOrdinal;
	}

	String getSubject() {
		return subject;
	}

	List<String> topicList() {
		return List.of(JSON.readValue(topics, String[].class));
	}

	int getMinutes() {
		return minutes;
	}

	boolean isDone() {
		return doneAt != null;
	}

}
