package com.gabarit.core.study;

import com.gabarit.core.exam.ExamDetail;
import com.gabarit.core.exam.ExamService;
import com.gabarit.core.exam.ExamSummary;
import com.gabarit.core.shared.BadRequestException;
import com.gabarit.core.shared.NotFoundException;
import com.gabarit.core.user.UserProfileService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Carteira: concursos que o usuário estuda, da lista ou cadastrados à mão. */
@Service
public class StudyTargetService {

	/** Bem acima do uso normal (quem estuda para 20 concursos ao mesmo tempo?), só para conter abuso. */
	public static final int MAX_TARGETS = 20;

	// "Prefeitura de Santos - SP" → órgão "Prefeitura de Santos" (a UF já está no concurso)
	private static final Pattern UF_SUFFIX = Pattern.compile("\\s+-\\s+[A-Z]{2}$");

	/** Item da carteira na lista. {@code exam}: o concurso da lista, se houver (situação e datas atuais). */
	public record StudyTargetSummary(UUID id, UUID examId, String title, String organ, String board, Integer year,
			String positionName, LocalDate examDate, Instant createdAt, ExamSummary exam) {
	}

	/**
	 * Item da carteira aberto: com o concurso da lista inteiro (os cargos para escolher).
	 * {@code suggestedPositions}: cargos do concurso que a pessoa segue no perfil (aparecem primeiro).
	 */
	public record StudyTargetDetail(UUID id, UUID examId, String title, String organ, String board, Integer year,
			String positionName, LocalDate examDate, Instant createdAt, ExamDetail exam,
			List<String> suggestedPositions) {
	}

	/** Resultado de adicionar: {@code created} falso = o concurso já estava na carteira. */
	public record Added(StudyTargetDetail target, boolean created) {
	}

	/** Campos editáveis (estado completo, como o PUT do perfil). */
	public record Details(String title, String organ, String board, Integer year, String positionName,
			LocalDate examDate) {
	}

	private final StudyTargetRepository repository;

	private final ExamService exams;

	private final UserProfileService profiles;

	StudyTargetService(StudyTargetRepository repository, ExamService exams, UserProfileService profiles) {
		this.repository = repository;
		this.exams = exams;
		this.profiles = profiles;
	}

	/** Mais recentes primeiro. Número fixo de queries: carteira + concursos da lista. */
	@Transactional(readOnly = true)
	public List<StudyTargetSummary> list(String userId) {
		List<StudyTarget> targets = repository.findByUserIdOrderByCreatedAtDescIdDesc(userId);
		Map<UUID, ExamSummary> byExamId = exams
			.summaries(targets.stream().map(StudyTarget::getExamId).filter(Objects::nonNull).toList())
			.stream()
			.collect(Collectors.toMap(ExamSummary::id, Function.identity()));
		return targets.stream().map(t -> summary(t, byExamId.get(t.getExamId()))).toList();
	}

	/** @throws NotFoundException não existe ou é de outro usuário (não revela qual) */
	@Transactional(readOnly = true)
	public StudyTargetDetail get(String userId, UUID id) {
		return detail(find(userId, id));
	}

	/**
	 * Põe um concurso da lista na carteira, já com título, órgão e ano, e com o cargo quando um só
	 * cargo do concurso está no perfil da pessoa. Se já estava, devolve o que existe (clicar duas
	 * vezes não duplica).
	 *
	 * @throws NotFoundException concurso que não existe
	 * @throws BadRequestException carteira cheia
	 */
	@Transactional
	public Added addExam(String userId, UUID examId) {
		var existing = repository.findByUserIdAndExamId(userId, examId);
		if (existing.isPresent()) {
			return new Added(detail(existing.get()), false);
		}
		ExamDetail exam = exams.get(examId);
		checkLimit(userId);
		LocalDate date = exam.registrationEnd() != null ? exam.registrationEnd() : exam.registrationStart();
		// o concurso chegou até a pessoa por um cargo do perfil: sendo um só, ele já vem escolhido
		List<String> suggested = suggestedPositions(userId, examId);
		var details = new Details(exam.title(), UF_SUFFIX.matcher(exam.title()).replaceFirst(""), null,
				date == null ? null : date.getYear(), suggested.size() == 1 ? suggested.getFirst() : null, null);
		// dois cliques simultâneos: o segundo viola a unique (user_id, exam_id) e vira 500; o web
		// desabilita o botão enquanto envia, e um novo clique cai no "já estava" acima
		return new Added(detail(repository.save(new StudyTarget(userId, examId, toEntity(details)))), true);
	}

	/**
	 * Concurso passado ou fora da lista: só o título; o resto vem depois (à mão ou pelo edital).
	 *
	 * @throws BadRequestException carteira cheia
	 */
	@Transactional
	public StudyTargetDetail addCustom(String userId, String title) {
		checkLimit(userId);
		var details = new Details(title.trim(), null, null, null, null, null);
		return detail(repository.save(new StudyTarget(userId, null, toEntity(details))));
	}

	/**
	 * Substitui os campos editáveis. Concurso da lista: o cargo tem de ser um dos cargos ativos dele.
	 *
	 * @throws BadRequestException cargo que não está no concurso
	 */
	@Transactional
	public StudyTargetDetail update(String userId, UUID id, Details details) {
		StudyTarget target = find(userId, id);
		var normalized = normalize(details);
		if (target.getExamId() != null && normalized.positionName() != null) {
			List<String> positions = exams.get(target.getExamId())
				.positions()
				.stream()
				.map(ExamDetail.Position::name)
				.toList();
			if (!positions.contains(normalized.positionName())) {
				throw new BadRequestException("cargo não está neste concurso: " + normalized.positionName());
			}
		}
		target.update(toEntity(normalized));
		return detail(target);
	}

	/** @throws NotFoundException não existe ou é de outro usuário */
	@Transactional
	public void remove(String userId, UUID id) {
		repository.delete(find(userId, id));
	}

	private StudyTarget find(String userId, UUID id) {
		return repository.findByIdAndUserId(id, userId)
			.orElseThrow(() -> new NotFoundException("concurso %s não está na sua carteira".formatted(id)));
	}

	private void checkLimit(String userId) {
		if (repository.countByUserId(userId) >= MAX_TARGETS) {
			throw new BadRequestException(
					"a carteira tem no máximo %d concursos: remova algum para adicionar outro".formatted(MAX_TARGETS));
		}
	}

	// texto em branco vira nulo (campo apagado no formulário), e tira espaços das pontas
	private static Details normalize(Details d) {
		return new Details(d.title().trim(), blankToNull(d.organ()), blankToNull(d.board()), d.year(),
				blankToNull(d.positionName()), d.examDate());
	}

	private static String blankToNull(String text) {
		return text == null || text.isBlank() ? null : text.trim();
	}

	private static StudyTarget.Details toEntity(Details d) {
		return new StudyTarget.Details(d.title(), d.organ(), d.board(), d.year(), d.positionName(), d.examDate());
	}

	private StudyTargetDetail detail(StudyTarget t) {
		ExamDetail exam = t.getExamId() == null ? null : exams.get(t.getExamId());
		List<String> suggested = exam == null ? List.of() : suggestedPositions(t.getUserId(), t.getExamId());
		return new StudyTargetDetail(t.getId(), t.getExamId(), t.getTitle(), t.getOrgan(), t.getBoard(), t.getYear(),
				t.getPositionName(), t.getExamDate(), t.getCreatedAt(), exam, suggested);
	}

	/** Cargos do concurso que a pessoa segue (a mesma regra do "meus concursos"). */
	private List<String> suggestedPositions(String userId, UUID examId) {
		return exams.matchingPositions(examId, profiles.followedCatalogSlugs(userId));
	}

	private static StudyTargetSummary summary(StudyTarget t, ExamSummary exam) {
		return new StudyTargetSummary(t.getId(), t.getExamId(), t.getTitle(), t.getOrgan(), t.getBoard(), t.getYear(),
				t.getPositionName(), t.getExamDate(), t.getCreatedAt(), exam);
	}

}
