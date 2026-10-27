package com.gabarit.core.exam;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Concurso coletado de uma fonte. Identidade: (source, externalId).
 * Só muda via {@link #apply}: o estado vem inteiro do evento, e evento atrasado não sobrescreve.
 */
@Entity
@Table(name = "exam")
public class Exam {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, updatable = false)
	private String source;

	@Column(nullable = false, updatable = false)
	private String externalId;

	@Column(nullable = false)
	private String title;

	@Column(nullable = false)
	private String url;

	private String uf;

	private String region;

	private String educationLevel;

	private String openings;

	private LocalDate registrationStart;

	private LocalDate registrationEnd;

	private String registrationNote;

	@Column(nullable = false)
	private Instant sourceUpdatedAt;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant updatedAt;

	@OneToMany(mappedBy = "exam", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("id")
	private List<ExamPosition> positions = new ArrayList<>();

	protected Exam() {
		// JPA
	}

	private Exam(String source, String externalId) {
		this.source = source;
		this.externalId = externalId;
	}

	public static Exam create(ExamSnapshot snapshot, Instant occurredAt) {
		Exam exam = new Exam(snapshot.source(), snapshot.externalId());
		exam.overwrite(snapshot, occurredAt);
		return exam;
	}

	/**
	 * Aplica o estado do evento se ele for mais novo que o último aplicado.
	 *
	 * @return false se o evento é mais antigo (ou o mesmo) e foi ignorado
	 */
	public boolean apply(ExamSnapshot snapshot, Instant occurredAt) {
		if (!source.equals(snapshot.source()) || !externalId.equals(snapshot.externalId())) {
			throw new IllegalArgumentException("snapshot de outro concurso: %s/%s em %s/%s"
					.formatted(snapshot.source(), snapshot.externalId(), source, externalId));
		}
		if (!occurredAt.isAfter(sourceUpdatedAt)) {
			return false;
		}
		overwrite(snapshot, occurredAt);
		return true;
	}

	private void overwrite(ExamSnapshot snapshot, Instant occurredAt) {
		if (snapshot.registrationStart() != null && snapshot.registrationEnd() != null
				&& snapshot.registrationEnd().isBefore(snapshot.registrationStart())) {
			throw new IllegalArgumentException("fim das inscrições antes do início: " + externalId);
		}
		this.title = snapshot.title();
		this.url = snapshot.url();
		this.uf = snapshot.uf();
		this.region = snapshot.region();
		this.educationLevel = snapshot.educationLevel();
		this.openings = snapshot.openings();
		this.registrationStart = snapshot.registrationStart();
		this.registrationEnd = snapshot.registrationEnd();
		this.registrationNote = snapshot.registrationNote();
		this.sourceUpdatedAt = occurredAt;
		syncPositions(snapshot.positions());
	}

	/**
	 * Deixa ativos exatamente os cargos da lista: cria os novos, reativa os que voltaram
	 * e inativa os que sumiram (sem apagar, porque planos podem referenciar o cargo).
	 */
	private void syncPositions(Collection<String> names) {
		Set<String> wanted = new LinkedHashSet<>(names);
		Map<String, ExamPosition> current = positions.stream()
				.collect(Collectors.toMap(ExamPosition::getName, Function.identity()));

		current.forEach((name, position) -> {
			if (wanted.contains(name)) {
				position.activate();
			}
			else {
				position.deactivate();
			}
		});
		wanted.stream()
				.filter(name -> !current.containsKey(name))
				.forEach(name -> positions.add(new ExamPosition(this, name)));
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

	public Long getId() {
		return id;
	}

	public String getSource() {
		return source;
	}

	public String getExternalId() {
		return externalId;
	}

	public String getTitle() {
		return title;
	}

	public String getUrl() {
		return url;
	}

	public String getUf() {
		return uf;
	}

	public String getRegion() {
		return region;
	}

	public String getEducationLevel() {
		return educationLevel;
	}

	public String getOpenings() {
		return openings;
	}

	public LocalDate getRegistrationStart() {
		return registrationStart;
	}

	public LocalDate getRegistrationEnd() {
		return registrationEnd;
	}

	public String getRegistrationNote() {
		return registrationNote;
	}

	public Instant getSourceUpdatedAt() {
		return sourceUpdatedAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	/** Todos os cargos, inclusive os inativos. */
	public List<ExamPosition> getPositions() {
		return Collections.unmodifiableList(positions);
	}

	public List<ExamPosition> getActivePositions() {
		return positions.stream().filter(ExamPosition::isActive).toList();
	}

}
