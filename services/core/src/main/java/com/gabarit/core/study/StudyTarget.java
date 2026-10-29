package com.gabarit.core.study;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Um concurso que o usuário estuda (item da carteira). {@code examId} liga ao concurso da lista;
 * nulo = concurso passado, cadastrado à mão. Órgão, banca, ano, cargo e data começam vazios ou
 * vindos da lista, e o usuário (ou, depois, a leitura do edital) completa.
 */
@Entity
@Table(name = "study_target")
public class StudyTarget {

	// UUID v7: ordenado pelo tempo (não espalha inserções pelo índice) e não dá para adivinhar
	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	@Column(nullable = false, updatable = false)
	private String userId;

	// só o id: o concurso é do pacote exam, lido pelo ExamService
	@Column(updatable = false)
	private UUID examId;

	@Column(nullable = false)
	private String title;

	private String organ;

	private String board;

	private Integer year;

	private String positionName;

	private LocalDate examDate;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant updatedAt;

	protected StudyTarget() {
		// JPA
	}

	StudyTarget(String userId, UUID examId, Details details) {
		this.userId = userId;
		this.examId = examId;
		update(details);
	}

	/** Campos que o usuário edita (o PUT manda todos: é o estado completo). */
	record Details(String title, String organ, String board, Integer year, String positionName,
			LocalDate examDate) {
	}

	void update(Details details) {
		this.title = details.title();
		this.organ = details.organ();
		this.board = details.board();
		this.year = details.year();
		this.positionName = details.positionName();
		this.examDate = details.examDate();
	}

	@PrePersist
	void onCreate() {
		createdAt = Instant.now();
		updatedAt = createdAt;
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = Instant.now();
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

	public String getTitle() {
		return title;
	}

	public String getOrgan() {
		return organ;
	}

	public String getBoard() {
		return board;
	}

	public Integer getYear() {
		return year;
	}

	public String getPositionName() {
		return positionName;
	}

	public LocalDate getExamDate() {
		return examDate;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
