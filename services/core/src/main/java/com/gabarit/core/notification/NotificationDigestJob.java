package com.gabarit.core.notification;

import com.gabarit.core.exam.ExamService;
import com.gabarit.core.exam.ExamStatus;
import com.gabarit.core.exam.ExamSummary;
import com.gabarit.core.user.UserProfileService;
import com.gabarit.core.user.UserProfileService.Contact;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Resumo diário por e-mail: um e-mail por usuário com os avisos que ainda não foram enviados.
 *
 * <p>Cada usuário numa transação própria: os avisos ficam travados enquanto o e-mail sai e só são
 * marcados como enviados se o envio deu certo (se falhar, tentam de novo no dia seguinte). Um
 * usuário com problema não impede os outros.
 */
@Component
class NotificationDigestJob {

	private static final Logger log = LoggerFactory.getLogger(NotificationDigestJob.class);

	// aviso mais velho que isso não vai mais por e-mail (ex.: quem religou o resumo depois de meses)
	static final Duration WINDOW = Duration.ofDays(7);

	// o sininho mostra só o recente; o resto é apagado
	static final Duration RETENTION = Duration.ofDays(90);

	private final NotificationRepository repository;

	private final UserProfileService profiles;

	private final ExamService exams;

	private final DigestMailer mailer;

	private final TransactionTemplate transaction;

	private final Clock clock;

	NotificationDigestJob(NotificationRepository repository, UserProfileService profiles, ExamService exams,
			DigestMailer mailer, TransactionTemplate transaction, Clock clock) {
		this.repository = repository;
		this.profiles = profiles;
		this.exams = exams;
		this.mailer = mailer;
		this.transaction = transaction;
		this.clock = clock;
	}

	@Scheduled(cron = "${gabarit.notifications.digest-cron}", zone = "America/Sao_Paulo")
	void scheduled() {
		run();
	}

	/** @return quantos e-mails foram enviados */
	int run() {
		Instant now = clock.instant();
		int deleted = transaction.execute(status -> repository.deleteOlderThan(now.minus(RETENTION)));
		if (deleted > 0) {
			log.info("{} avisos antigos apagados", deleted);
		}
		if (!mailer.enabled()) {
			log.warn("resumo diário não enviado: SMTP não configurado (SMTP_HOST)");
			return 0;
		}
		Instant since = now.minus(WINDOW);
		List<Contact> contacts = profiles.digestContacts(repository.findUsersWithPendingEmail(since));
		int sent = 0;
		for (Contact contact : contacts) {
			try {
				if (Boolean.TRUE.equals(transaction.execute(status -> sendTo(contact, since)))) {
					sent++;
				}
			}
			catch (RuntimeException e) {
				// só o id: e-mail é dado pessoal e não vai para o log
				log.error("falha no resumo do usuário {}; tenta de novo no próximo", contact.userId(), e);
			}
		}
		log.info("resumo diário: {} e-mails enviados", sent);
		return sent;
	}

	private boolean sendTo(Contact contact, Instant since) {
		List<Notification> pending = repository.lockPendingEmail(contact.userId(), since);
		if (pending.isEmpty()) {
			// outra instância pegou este usuário (SKIP LOCKED) ou já foi enviado
			return false;
		}
		// inscrição que acabou desde o aviso não vale o e-mail (continua no sininho)
		List<ExamSummary> open = exams.summaries(pending.stream().map(Notification::getExamId).toList())
			.stream()
			.filter(exam -> exam.status() != ExamStatus.CLOSED)
			.toList();
		if (!open.isEmpty()) {
			mailer.send(contact, open);
		}
		Instant now = clock.instant();
		pending.forEach(notification -> notification.markEmailed(now));
		return !open.isEmpty();
	}

}
