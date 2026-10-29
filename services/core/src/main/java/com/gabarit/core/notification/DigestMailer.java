package com.gabarit.core.notification;

import com.gabarit.core.exam.ExamSummary;
import com.gabarit.core.user.UserProfileService.Contact;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.io.UnsupportedEncodingException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * E-mail do resumo diário: concursos novos para os cargos do usuário, com link para cada um no app.
 * HTML simples com estilo inline (o que os clientes de e-mail aceitam) e versão em texto puro.
 */
@Component
class DigestMailer {

	// e-mail longo demais ninguém lê: o resto fica no app
	static final int MAX_EXAMS = 20;

	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	private final ObjectProvider<JavaMailSender> sender;

	private final String host;

	private final String from;

	private final String webUrl;

	DigestMailer(ObjectProvider<JavaMailSender> sender, @Value("${spring.mail.host:}") String host,
			@Value("${gabarit.notifications.from}") String from, @Value("${gabarit.web-url}") String webUrl) {
		this.sender = sender;
		this.host = host;
		this.from = from;
		this.webUrl = webUrl;
	}

	/** SMTP_HOST vazio (ex.: ambiente sem e-mail): o resumo não é enviado. */
	boolean enabled() {
		return !host.isBlank() && sender.getIfAvailable() != null;
	}

	/** @throws org.springframework.mail.MailException SMTP fora do ar, endereço recusado... */
	void send(Contact contact, List<ExamSummary> exams) {
		JavaMailSender mail = sender.getObject();
		MimeMessage message = mail.createMimeMessage();
		try {
			var helper = new MimeMessageHelper(message, true, "UTF-8");
			helper.setFrom(from, "gabarit.ai");
			helper.setTo(contact.email());
			helper.setSubject(subject(exams.size()));
			helper.setText(text(contact, exams), html(contact, exams));
		}
		catch (MessagingException | UnsupportedEncodingException e) {
			throw new MailPreparationException("não foi possível montar o resumo", e);
		}
		mail.send(message);
	}

	static String subject(int count) {
		return count == 1 ? "1 concurso novo para você" : "%d concursos novos para você".formatted(count);
	}

	private static String greeting(Contact contact) {
		return contact.givenName() == null || contact.givenName().isBlank() ? "Olá!"
				: "Olá, %s!".formatted(contact.givenName());
	}

	private static String intro(int count) {
		return count == 1 ? "Abriu um concurso para os cargos que você acompanha:"
				: "Abriram %d concursos para os cargos que você acompanha:".formatted(count);
	}

	/** "SP · Inscrições até 20/11/2026 · 10 vagas até R$ 5.000,00" */
	private static String details(ExamSummary exam) {
		String place = exam.uf() == null ? "Nacional" : exam.uf();
		String registration = switch (exam.status()) {
			case OPEN -> exam.registrationEnd() == null ? "Inscrições abertas"
					: "Inscrições até " + day(exam.registrationEnd());
			case UPCOMING -> exam.registrationStart() == null ? "Inscrições em breve"
					: "Inscrições a partir de " + day(exam.registrationStart());
			case UNKNOWN, CLOSED -> null;
		};
		var parts = new ArrayList<String>();
		parts.add(place);
		if (registration != null) {
			parts.add(registration);
		}
		if (exam.openings() != null) {
			parts.add(exam.openings());
		}
		return String.join(" · ", parts);
	}

	private static String day(LocalDate date) {
		return DAY.format(date);
	}

	private String examUrl(ExamSummary exam) {
		return "%s/concursos/%s".formatted(webUrl, exam.id());
	}

	private String text(Contact contact, List<ExamSummary> exams) {
		var out = new StringBuilder();
		out.append(greeting(contact)).append("\n\n").append(intro(exams.size())).append("\n\n");
		exams.stream().limit(MAX_EXAMS).forEach(exam -> out.append("- ")
			.append(exam.title())
			.append("\n  ")
			.append(details(exam))
			.append("\n  ")
			.append(examUrl(exam))
			.append("\n\n"));
		if (exams.size() > MAX_EXAMS) {
			out.append("E mais ").append(exams.size() - MAX_EXAMS).append(" no app: ").append(webUrl).append("\n\n");
		}
		out.append("Você recebe este resumo porque acompanha cargos no gabarit.ai. Para parar, desligue em ")
			.append(webUrl)
			.append("/perfil\n");
		return out.toString();
	}

	private String html(Contact contact, List<ExamSummary> exams) {
		var items = new StringBuilder();
		exams.stream().limit(MAX_EXAMS).forEach(exam -> items.append("""
				<tr><td style="padding:14px 0;border-bottom:1px solid #e2e8f0">
				  <a href="%s" style="color:#4338ca;font-weight:600;font-size:15px;text-decoration:none">%s</a>
				  <div style="color:#64748b;font-size:13px;margin-top:4px">%s</div>
				</td></tr>
				""".formatted(escape(examUrl(exam)), escape(exam.title()), escape(details(exam)))));
		String more = exams.size() <= MAX_EXAMS ? ""
				: "<p style=\"color:#64748b;font-size:14px\">E mais %d no app.</p>".formatted(exams.size() - MAX_EXAMS);
		return """
				<!doctype html>
				<html lang="pt-BR"><body style="margin:0;background:#f6f7fb;font-family:Arial,Helvetica,sans-serif;color:#0b1020">
				<table role="presentation" width="100%%" cellpadding="0" cellspacing="0"><tr><td align="center" style="padding:24px 12px">
				<table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="max-width:560px;background:#ffffff;border-radius:16px;padding:28px">
				  <tr><td style="font-size:20px;font-weight:700">gabarit.ai</td></tr>
				  <tr><td style="padding-top:20px;font-size:15px">%s</td></tr>
				  <tr><td style="padding-top:8px;font-size:15px;color:#334155">%s</td></tr>
				  <tr><td><table role="presentation" width="100%%" cellpadding="0" cellspacing="0">%s</table></td></tr>
				  <tr><td>%s</td></tr>
				  <tr><td style="padding-top:20px">
				    <a href="%s" style="display:inline-block;background:#4f46e5;color:#ffffff;font-weight:600;font-size:14px;padding:12px 20px;border-radius:10px;text-decoration:none">Ver concursos no app</a>
				  </td></tr>
				  <tr><td style="padding-top:28px;font-size:12px;color:#94a3b8">
				    Você recebe este resumo porque acompanha cargos no gabarit.ai.
				    <a href="%s/perfil" style="color:#94a3b8">Desligar o resumo por e-mail</a>
				  </td></tr>
				</table></td></tr></table>
				</body></html>
				""".formatted(escape(greeting(contact)), escape(intro(exams.size())), items, more, escape(webUrl),
				escape(webUrl));
	}

	private static String escape(String text) {
		return HtmlUtils.htmlEscape(text, "UTF-8");
	}

}
