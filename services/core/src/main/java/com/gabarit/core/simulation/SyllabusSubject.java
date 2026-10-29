package com.gabarit.core.simulation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

/** Disciplina do conteúdo de um cargo (leitura EDITAL_SYLLABUS), copiada para os tópicos terem id. */
@Entity
@Table(name = "syllabus_subject")
class SyllabusSubject {

	// UUID v7: ordenado pelo tempo (não espalha inserções pelo índice) e não dá para adivinhar
	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	@Column(nullable = false, updatable = false)
	private UUID extractionId;

	@Column(nullable = false, updatable = false)
	private int ordinal;

	@Column(nullable = false, updatable = false)
	private String name;

	@Column(updatable = false)
	private String groupName;

	protected SyllabusSubject() {
		// JPA
	}

	SyllabusSubject(UUID extractionId, int ordinal, String name, String groupName) {
		this.extractionId = extractionId;
		this.ordinal = ordinal;
		this.name = name;
		this.groupName = groupName;
	}

	UUID getId() {
		return id;
	}

	int getOrdinal() {
		return ordinal;
	}

	String getName() {
		return name;
	}

}
