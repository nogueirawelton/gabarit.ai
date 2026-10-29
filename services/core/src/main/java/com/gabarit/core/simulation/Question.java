package com.gabarit.core.simulation;

import com.gabarit.core.study.ExamPaper;
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

import java.util.List;
import java.util.UUID;

/**
 * Questão de uma prova lida (leitura EXAM_PAPER), copiada do resultado para o simulado apontar por
 * id. É do conteúdo, não do usuário: todos que leram a mesma prova usam as mesmas questões.
 */
@Entity
@Table(name = "question")
class Question {

	enum Kind {

		MULTIPLE_CHOICE, TRUE_FALSE, ESSAY

	}

	private static final JsonMapper JSON = JsonMapper.builder().build();

	// UUID v7: ordenado pelo tempo (não espalha inserções pelo índice) e não dá para adivinhar
	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	@Column(nullable = false, updatable = false)
	private UUID extractionId;

	@Column(nullable = false, updatable = false)
	private int ordinal;

	@Column(nullable = false, updatable = false)
	private int number;

	@Column(updatable = false)
	private String subject;

	@Column(nullable = false, updatable = false)
	@Enumerated(EnumType.STRING)
	private Kind kind;

	@Column(updatable = false)
	private String baseText;

	@Column(nullable = false, updatable = false)
	private String statement;

	// JSON já serializado ([{"letter": "A", "text": "..."}]): o web recebe a lista
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, updatable = false)
	private String options;

	@Column(updatable = false)
	private String answer;

	@Column(nullable = false, updatable = false)
	private boolean annulled;

	@Column(nullable = false, updatable = false)
	private boolean hasImage;

	@Column(updatable = false)
	private Integer page;

	// gerada pela LLM para um tópico (não é de prova real), com o comentário da resposta
	@Column(nullable = false, updatable = false)
	private boolean generated;

	@Column(updatable = false)
	private String explanation;

	protected Question() {
		// JPA
	}

	Question(UUID extractionId, int ordinal, int number, String subject, Kind kind, String baseText, String statement,
			String options, String answer, boolean annulled, boolean hasImage, Integer page) {
		this.extractionId = extractionId;
		this.ordinal = ordinal;
		this.number = number;
		this.subject = subject;
		this.kind = kind;
		this.baseText = baseText;
		this.statement = statement;
		this.options = options;
		this.answer = answer;
		this.annulled = annulled;
		this.hasImage = hasImage;
		this.page = page;
	}

	/** Questão inédita gerada para um tópico (TOPIC_QUESTIONS): sem texto-base, imagem nem página. */
	static Question generated(UUID extractionId, int ordinal, String subject, Kind kind, String statement,
			String options, String answer, String explanation) {
		Question question = new Question(extractionId, ordinal, ordinal + 1, subject, kind, null, statement, options,
				answer, false, false, null);
		question.generated = true;
		question.explanation = explanation;
		return question;
	}

	UUID getId() {
		return id;
	}

	UUID getExtractionId() {
		return extractionId;
	}

	int getOrdinal() {
		return ordinal;
	}

	int getNumber() {
		return number;
	}

	String getSubject() {
		return subject;
	}

	Kind getKind() {
		return kind;
	}

	String getBaseText() {
		return baseText;
	}

	String getStatement() {
		return statement;
	}

	/** As alternativas (vazia em certo/errado e discursiva). */
	List<ExamPaper.Option> optionList() {
		return List.of(JSON.readValue(options, ExamPaper.Option[].class));
	}

	String getAnswer() {
		return answer;
	}

	boolean isAnnulled() {
		return annulled;
	}

	boolean hasImage() {
		return hasImage;
	}

	Integer getPage() {
		return page;
	}

	boolean isGenerated() {
		return generated;
	}

	String getExplanation() {
		return explanation;
	}

}
