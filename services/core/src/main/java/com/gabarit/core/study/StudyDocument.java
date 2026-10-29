package com.gabarit.core.study;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Arquivo de alguém num concurso da carteira. O conteúdo é um blob do pacote document (pelo hash);
 * aqui fica o que o arquivo é para essa pessoa e se o envio foi aprovado. Edital sem prova relacionada é
 * o do concurso-alvo; prova, gabarito e o edital daquela prova ocupam os espaços de uma prova
 * anterior ({@link StudyPaper}).
 */
@Entity
@Table(name = "study_document")
public class StudyDocument {

	public enum Kind {

		EDITAL, PROVA, GABARITO

	}

	public enum Status {

		/** URL de envio entregue; esperando o navegador enviar e confirmar. */
		UPLOADING, READY, REJECTED

	}

	// UUID v7: ordenado pelo tempo (não espalha inserções pelo índice) e não dá para adivinhar
	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	@Column(nullable = false, updatable = false)
	private String userId;

	@Column(nullable = false, updatable = false)
	private UUID studyTargetId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false)
	private Kind kind;

	// prova relacionada dona do arquivo; nulo no edital do concurso-alvo
	@Column(updatable = false)
	private UUID paperId;

	@Column(nullable = false, updatable = false)
	private String originalName;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Status status;

	private String rejectionReason;

	private String blobSha256;

	// copiados do blob na aprovação: a lista mostra sem precisar do pacote document
	private Long sizeBytes;

	private Integer pages;

	// leitura do edital pela LLM (pacote extraction); só no edital do concurso-alvo
	private UUID extractionId;

	// cargo escolhido entre os da leitura, como o edital escreve (especialidade nula = cargo sem áreas)
	private String positionName;

	private String positionSpecialty;

	// leitura do conteúdo programático do cargo escolhido (pacote extraction)
	private UUID syllabusExtractionId;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	protected StudyDocument() {
		// JPA
	}

	StudyDocument(String userId, UUID studyTargetId, Kind kind, UUID paperId, String originalName) {
		this.userId = userId;
		this.studyTargetId = studyTargetId;
		this.kind = kind;
		this.paperId = paperId;
		this.originalName = originalName;
		this.status = Status.UPLOADING;
	}

	void ready(String blobSha256, long sizeBytes, int pages) {
		this.status = Status.READY;
		this.blobSha256 = blobSha256;
		this.sizeBytes = sizeBytes;
		this.pages = pages;
	}

	/**
	 * Outra leitura do mesmo edital (prompt novo) mantém o cargo escolhido: os cargos são os mesmos
	 * na prática, e o conteúdo dele não depende da visão geral (é pedido pelo nome do cargo). O web
	 * avisa se o cargo não aparece na leitura nova.
	 */
	void reading(UUID extractionId) {
		this.extractionId = extractionId;
	}

	/** Outro cargo: o conteúdo lido era do anterior, a leitura dele deixa de valer. */
	void choosePosition(String name, String specialty) {
		if (!Objects.equals(name, positionName) || !Objects.equals(specialty, positionSpecialty)) {
			this.syllabusExtractionId = null;
		}
		this.positionName = name;
		this.positionSpecialty = specialty;
	}

	void syllabus(UUID extractionId) {
		this.syllabusExtractionId = extractionId;
	}

	/** Edital do concurso-alvo (não o de uma prova relacionada): o que a LLM lê para preencher o concurso. */
	boolean isTargetEdital() {
		return kind == Kind.EDITAL && paperId == null;
	}

	void reject(String reason) {
		this.status = Status.REJECTED;
		this.rejectionReason = reason;
	}

	@PrePersist
	void onCreate() {
		createdAt = Instant.now();
	}

	/** Chave do envio no MinIO: única e sem dados que o usuário digitou. */
	String uploadId() {
		return "%s/%s".formatted(userId, id);
	}

	public UUID getId() {
		return id;
	}

	public String getUserId() {
		return userId;
	}

	public UUID getStudyTargetId() {
		return studyTargetId;
	}

	public Kind getKind() {
		return kind;
	}

	public UUID getPaperId() {
		return paperId;
	}

	public String getOriginalName() {
		return originalName;
	}

	public Status getStatus() {
		return status;
	}

	public String getRejectionReason() {
		return rejectionReason;
	}

	public String getBlobSha256() {
		return blobSha256;
	}

	public Long getSizeBytes() {
		return sizeBytes;
	}

	public Integer getPages() {
		return pages;
	}

	public UUID getExtractionId() {
		return extractionId;
	}

	public String getPositionName() {
		return positionName;
	}

	public String getPositionSpecialty() {
		return positionSpecialty;
	}

	public UUID getSyllabusExtractionId() {
		return syllabusExtractionId;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
