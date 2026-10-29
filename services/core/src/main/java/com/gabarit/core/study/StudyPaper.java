package com.gabarit.core.study;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * Prova relacionada de um concurso da carteira, com três espaços: o PDF da prova, o do gabarito dela
 * (ou nenhum, quando o gabarito vem junto) e, opcional, o edital daquela prova. O par prova ↔
 * gabarito fica explícito, e as questões podem ser lidas pelo conteúdo do edital do mesmo ano.
 */
@Entity
@Table(name = "study_paper")
public class StudyPaper {

	// UUID v7: ordenado pelo tempo (não espalha inserções pelo índice) e não dá para adivinhar
	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	@Column(nullable = false, updatable = false)
	private String userId;

	@Column(nullable = false, updatable = false)
	private UUID studyTargetId;

	private Integer year;

	private String variant;

	@Column(nullable = false)
	private boolean answersIncluded;

	// leitura da prova pela LLM (pacote extraction): as questões
	private UUID extractionId;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	protected StudyPaper() {
		// JPA
	}

	StudyPaper(String userId, UUID studyTargetId, Integer year, String variant, boolean answersIncluded) {
		this.userId = userId;
		this.studyTargetId = studyTargetId;
		this.year = year;
		this.variant = variant;
		this.answersIncluded = answersIncluded;
	}

	/** Leitura das questões; nulo quando o PDF da prova ou do gabarito muda (a leitura era de outro). */
	void reading(UUID extractionId) {
		this.extractionId = extractionId;
	}

	@PrePersist
	void onCreate() {
		createdAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public Integer getYear() {
		return year;
	}

	public String getVariant() {
		return variant;
	}

	public boolean isAnswersIncluded() {
		return answersIncluded;
	}

	public UUID getExtractionId() {
		return extractionId;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
