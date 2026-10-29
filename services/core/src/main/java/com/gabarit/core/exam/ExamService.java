package com.gabarit.core.exam;

import com.gabarit.core.shared.NotFoundException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Porta de entrada do pacote exam: outros pacotes usam isto, nunca o repositório. */
@Service
public class ExamService {

	public enum UpsertResult {

		CREATED, UPDATED,
		/** Evento mais antigo (ou repetido) que o último aplicado. */
		IGNORED

	}

	/** Maior página da listagem (os controllers validam o parâmetro com isto). */
	public static final int MAX_PAGE_SIZE = 50;

	private final ExamRepository repository;

	private final ExamPositionRepository positionRepository;

	private final Clock clock;

	private final ApplicationEventPublisher events;

	ExamService(ExamRepository repository, ExamPositionRepository positionRepository, Clock clock,
			ApplicationEventPublisher events) {
		this.repository = repository;
		this.positionRepository = positionRepository;
		this.clock = clock;
		this.events = events;
	}

	/**
	 * Cria ou atualiza o concurso pela identidade (source, externalId). Idempotente: o mesmo
	 * evento entregue de novo não muda nada.
	 *
	 * @throws IllegalArgumentException se os dados do concurso são inválidos (ex.: datas)
	 */
	@Transactional
	public UpsertResult upsert(ExamSnapshot snapshot, Instant occurredAt) {
		return repository.findBySourceAndExternalId(snapshot.source(), snapshot.externalId())
				.map(exam -> exam.apply(snapshot, occurredAt) ? UpsertResult.UPDATED : UpsertResult.IGNORED)
				.orElseGet(() -> {
					// dois consumidores criando o mesmo concurso ao mesmo tempo: o segundo viola a
					// unique, a mensagem volta pra fila e, na reentrega, cai no apply acima
					Exam exam = repository.save(Exam.create(snapshot, occurredAt));
					// só na criação: reentrega e atualização não avisam de novo
					events.publishEvent(createdEvent(exam));
					return UpsertResult.CREATED;
				});
	}

	private ExamCreatedEvent createdEvent(Exam exam) {
		Set<String> slugs = exam.getPositions()
			.stream()
			.filter(ExamPosition::isActive)
			.flatMap(position -> position.getCatalogSlugs().stream())
			.collect(Collectors.toUnmodifiableSet());
		return new ExamCreatedEvent(exam.getId(), exam.getSource(), exam.getUf(), exam.getEducationLevels(), slugs,
				ExamStatus.of(exam.getRegistrationStart(), exam.getRegistrationEnd(), LocalDate.now(clock)));
	}

	/**
	 * Concursos por id, na ordem pedida (id que não existe é ignorado). Uma query.
	 */
	@Transactional(readOnly = true)
	public List<ExamSummary> summaries(List<UUID> ids) {
		LocalDate today = LocalDate.now(clock);
		Map<UUID, Exam> byId = repository.findAllById(ids)
			.stream()
			.collect(Collectors.toMap(Exam::getId, Function.identity()));
		return ids.stream()
			.map(byId::get)
			.filter(Objects::nonNull)
			.map(exam -> ExamSummary.of(exam, today, List.of()))
			.toList();
	}

	/**
	 * Página de concursos com os filtros, na ordem fixa de {@link ExamSpecifications#matching}.
	 * Número fixo de queries: contagem + página (+ cargos seguidos da página, se filtrou por cargo).
	 */
	@Transactional(readOnly = true)
	public Page<ExamSummary> search(ExamFilter filter, int page, int size) {
		LocalDate today = LocalDate.now(clock);
		Page<Exam> exams = repository.findAll(ExamSpecifications.matching(filter, today), PageRequest.of(page, size));
		Map<UUID, List<String>> matching = filter.followsPositions()
				? matchingPositions(exams.getContent(), filter.catalogSlugs()) : Map.of();
		return exams.map(exam -> ExamSummary.of(exam, today, matching.getOrDefault(exam.getId(), List.of())));
	}

	/** @throws NotFoundException concurso que não existe */
	@Transactional(readOnly = true)
	public ExamDetail get(UUID id) {
		return repository.findWithPositionsById(id)
			.map(exam -> ExamDetail.of(exam, LocalDate.now(clock)))
			.orElseThrow(() -> new NotFoundException("concurso %s não encontrado".formatted(id)));
	}

	/**
	 * Cargos ativos do concurso que estão entre os seguidos (mesma regra da busca por cargo), em ordem
	 * alfabética. Concurso que não existe: lista vazia.
	 */
	@Transactional(readOnly = true)
	public List<String> matchingPositions(UUID examId, Map<String, Set<String>> slugsBySource) {
		if (slugsBySource.isEmpty()) {
			return List.of();
		}
		return repository.findById(examId)
			.map(exam -> matchingPositions(List.of(exam), slugsBySource).getOrDefault(examId, List.of()))
			.orElse(List.of());
	}

	// nomes dos cargos ativos que batem com os seguidos, por concurso (mesma regra do filtro, em Java)
	private Map<UUID, List<String>> matchingPositions(Collection<Exam> exams, Map<String, Set<String>> slugsBySource) {
		if (exams.isEmpty()) {
			return Map.of();
		}
		Map<UUID, Exam> byId = exams.stream().collect(Collectors.toMap(Exam::getId, Function.identity()));
		return positionRepository.findByExamIdInAndActiveTrue(byId.keySet())
			.stream()
			.filter(position -> {
				// getExam().getId() não inicializa o proxy: o id já está no cargo
				Set<String> followed = slugsBySource.getOrDefault(byId.get(position.getExam().getId()).getSource(),
						Set.of());
				return position.getCatalogSlugs().stream().anyMatch(followed::contains);
			})
			.collect(Collectors.groupingBy(position -> position.getExam().getId(),
					Collectors.mapping(ExamPosition::getName, Collectors.collectingAndThen(Collectors.toList(),
							names -> names.stream().sorted().toList()))));
	}

}
