package com.gabarit.core.notification;

import com.gabarit.core.exam.ExamCreatedEvent;
import com.gabarit.core.exam.ExamService;
import com.gabarit.core.exam.ExamStatus;
import com.gabarit.core.exam.ExamSummary;
import com.gabarit.core.shared.NotFoundException;
import com.gabarit.core.user.UserProfileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Avisos de concurso novo: criados quando um concurso aparece (mesmas regras do "meus concursos"),
 * lidos no sininho do app e enviados no resumo diário por e-mail ({@link NotificationDigestJob}).
 */
@Service
public class NotificationService {

	private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

	/** Aviso com o concurso já resolvido, para a API. */
	public record NotificationView(UUID id, Instant createdAt, boolean read, ExamSummary exam) {
	}

	private final NotificationRepository repository;

	private final UserProfileService profiles;

	private final ExamService exams;

	private final Clock clock;

	NotificationService(NotificationRepository repository, UserProfileService profiles, ExamService exams,
			Clock clock) {
		this.repository = repository;
		this.profiles = profiles;
		this.exams = exams;
		this.clock = clock;
	}

	/**
	 * Avisa quem se interessa pelo concurso que acabou de aparecer. Roda dentro da transação que o
	 * criou (evento síncrono): se falhar, o concurso também não é gravado e a mensagem volta para a
	 * fila. Assim cada interessado recebe o aviso uma vez, nem zero nem duas.
	 * Concurso que já chega encerrado não gera aviso.
	 */
	@EventListener
	@Transactional
	public void onExamCreated(ExamCreatedEvent event) {
		if (event.status() == ExamStatus.CLOSED) {
			return;
		}
		List<String> userIds = profiles.findInterested(event.source(), event.catalogSlugs(), event.uf(),
				event.educationLevels());
		Instant now = clock.instant();
		repository.saveAll(userIds.stream().map(userId -> new Notification(userId, event.examId(), now)).toList());
		if (!userIds.isEmpty()) {
			log.info("concurso {} avisado para {} usuários", event.examId(), userIds.size());
		}
	}

	/** Avisos do usuário, mais novos primeiro. Número fixo de queries: avisos + concursos da página. */
	@Transactional(readOnly = true)
	public Page<NotificationView> list(String userId, int page, int size) {
		Page<Notification> notifications = repository.findByUserIdOrderByCreatedAtDescIdDesc(userId,
				PageRequest.of(page, size));
		Map<UUID, ExamSummary> byExamId = exams
			.summaries(notifications.stream().map(Notification::getExamId).toList())
			.stream()
			.collect(Collectors.toMap(ExamSummary::id, Function.identity()));
		// concurso apagado some do aviso (o FK com cascade apaga o aviso também; isto cobre a corrida)
		List<NotificationView> views = notifications.stream()
			.filter(n -> byExamId.containsKey(n.getExamId()))
			.map(n -> new NotificationView(n.getId(), n.getCreatedAt(), n.getReadAt() != null,
					byExamId.get(n.getExamId())))
			.toList();
		return new PageImpl<>(views, notifications.getPageable(), notifications.getTotalElements());
	}

	@Transactional(readOnly = true)
	public long unreadCount(String userId) {
		return repository.countByUserIdAndReadAtIsNull(userId);
	}

	/** @throws NotFoundException aviso que não existe ou é de outro usuário (não revela qual dos dois) */
	@Transactional
	public void markRead(String userId, UUID id) {
		repository.findByIdAndUserId(id, userId)
			.orElseThrow(() -> new NotFoundException("aviso %s não encontrado".formatted(id)))
			.markRead(clock.instant());
	}

	@Transactional
	public void markAllRead(String userId) {
		repository.markAllRead(userId, clock.instant());
	}

}
