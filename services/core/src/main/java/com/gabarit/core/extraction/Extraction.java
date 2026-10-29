package com.gabarit.core.extraction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Uma leitura do worker extraction, única por (tarefa, entradas, versão do prompt). É também o
 * cache: pedido igual devolve esta, de qualquer usuário.
 */
@Entity
@Table(name = "extraction")
public class Extraction {

	public enum Status {

		PENDING, DONE, FAILED

	}

	// UUID v7: ordenado pelo tempo (não espalha inserções pelo índice) e não dá para adivinhar
	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false)
	private ExtractionTask task;

	@Column(nullable = false, updatable = false)
	private String inputsHash;

	@Column(nullable = false, updatable = false)
	private String promptVersion;

	// JSON já serializado: o core guarda e devolve, não interpreta
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, updatable = false)
	private String inputs;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Status status;

	@JdbcTypeCode(SqlTypes.JSON)
	private String result;

	private String failureReason;

	private Boolean retryable;

	private String model;

	private Integer inputTokens;

	private Integer outputTokens;

	private Integer cacheReadInputTokens;

	private Integer cacheCreationInputTokens;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant requestedAt;

	private Instant completedAt;

	protected Extraction() {
		// JPA
	}

	Extraction(ExtractionTask task, String inputsHash, String inputs, Instant now) {
		this.task = task;
		this.inputsHash = inputsHash;
		this.promptVersion = task.promptVersion();
		this.inputs = inputs;
		this.status = Status.PENDING;
		this.createdAt = now;
		this.requestedAt = now;
	}

	/** Falha que pode dar certo pedindo de novo (erro transitório no worker ou no envio). */
	boolean canRetry() {
		return status == Status.FAILED && Boolean.TRUE.equals(retryable);
	}

	void retry(Instant now) {
		status = Status.PENDING;
		failureReason = null;
		retryable = null;
		requestedAt = now;
	}

	/** @return false se já não estava pendente (resultado repetido ou atrasado: ignorado) */
	boolean complete(String result, String model, Usage usage, Instant now) {
		if (status != Status.PENDING) {
			return false;
		}
		this.status = Status.DONE;
		this.result = result;
		this.model = model;
		this.inputTokens = usage.inputTokens();
		this.outputTokens = usage.outputTokens();
		this.cacheReadInputTokens = usage.cacheReadInputTokens();
		this.cacheCreationInputTokens = usage.cacheCreationInputTokens();
		this.completedAt = now;
		return true;
	}

	/** @return false se já não estava pendente (resultado repetido ou atrasado: ignorado) */
	boolean fail(String reason, boolean retryable, Instant now) {
		if (status != Status.PENDING) {
			return false;
		}
		this.status = Status.FAILED;
		this.failureReason = reason;
		this.retryable = retryable;
		this.completedAt = now;
		return true;
	}

	public UUID getId() {
		return id;
	}

	public ExtractionTask getTask() {
		return task;
	}

	public String getInputsHash() {
		return inputsHash;
	}

	public String getPromptVersion() {
		return promptVersion;
	}

	String getInputs() {
		return inputs;
	}

	public Status getStatus() {
		return status;
	}

	/** JSON do resultado (só em DONE), no formato do schema da tarefa. */
	public String getResult() {
		return result;
	}

	public String getFailureReason() {
		return failureReason;
	}

	/** Falha que pode dar certo pedindo de novo (só em FAILED). */
	public boolean isRetryable() {
		return Boolean.TRUE.equals(retryable);
	}

	public String getModel() {
		return model;
	}

	public Instant getCompletedAt() {
		return completedAt;
	}

	/** Tokens gastos numa leitura. */
	record Usage(int inputTokens, int outputTokens, int cacheReadInputTokens, int cacheCreationInputTokens) {
	}

}
