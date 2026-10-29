package com.gabarit.core.simulation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

/** Tópico do conteúdo de um cargo. O código ("3.12": disciplina.tópico, a partir de 1) é o que a LLM devolve. */
@Entity
@Table(name = "syllabus_topic")
class SyllabusTopic {

	// UUID v7: ordenado pelo tempo (não espalha inserções pelo índice) e não dá para adivinhar
	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "subject_id", nullable = false, updatable = false)
	private SyllabusSubject subject;

	@Column(nullable = false, updatable = false)
	private int ordinal;

	@Column(nullable = false, updatable = false)
	private String text;

	protected SyllabusTopic() {
		// JPA
	}

	SyllabusTopic(SyllabusSubject subject, int ordinal, String text) {
		this.subject = subject;
		this.ordinal = ordinal;
		this.text = text;
	}

	UUID getId() {
		return id;
	}

	SyllabusSubject getSubject() {
		return subject;
	}

	int getOrdinal() {
		return ordinal;
	}

	String getText() {
		return text;
	}

	String code() {
		return "%d.%d".formatted(subject.getOrdinal() + 1, ordinal + 1);
	}

}
