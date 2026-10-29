package com.gabarit.core.notification;

import com.gabarit.core.PostgresTestConfiguration;
import com.gabarit.core.exam.CatalogPositionService;
import com.gabarit.core.exam.ExamService;
import com.gabarit.core.user.UserProfileService;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.mail.autoconfigure.MailSenderAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Resumo diário com SMTP de verdade (GreenMail) e o banco real. */
@DataJpaTest(properties = { "spring.mail.host=localhost", "spring.mail.port=3025",
		"spring.mail.properties.mail.smtp.auth=false", "spring.mail.properties.mail.smtp.starttls.enable=false",
		"gabarit.notifications.from=no-reply@gabarit.test", "gabarit.web-url=https://app.gabarit.test" })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(MailSenderAutoConfiguration.class)
@Import({ PostgresTestConfiguration.class, ExamService.class, CatalogPositionService.class, UserProfileService.class,
		NotificationService.class, NotificationDigestJob.class, DigestMailer.class, NotificationTest.FixedClock.class })
class NotificationDigestJobTest {

	@RegisterExtension
	static final GreenMailExtension smtp = new GreenMailExtension(ServerSetupTest.SMTP);

	@Autowired
	NotificationDigestJob job;

	@Autowired
	ExamService exams;

	@Autowired
	CatalogPositionService catalog;

	@Autowired
	UserProfileService profiles;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	TestEntityManager em;

	/** SQL direto no banco: grava o que está na sessão antes e esquece o cache depois. */
	void sql(String statement) {
		em.flush();
		jdbc.update(statement);
		em.clear();
	}

	@BeforeEach
	void perfis() {
		catalog.sync("pciconcursos", List.of(new CatalogPositionService.Entry("enfermeiro", "Enfermeiro")),
				NotificationTest.T1);
		UUID enfermeiro = catalog.list("pciconcursos").getFirst().getId();
		for (String user : List.of("maria", "sem-confirmar", "desligou")) {
			profiles.update(user, List.of(enfermeiro), List.of(), List.of());
		}
		profiles.rememberContact("maria", "maria@exemplo.com", true, "Maria");
		profiles.rememberContact("sem-confirmar", "outra@exemplo.com", false, "Outra");
		profiles.rememberContact("desligou", "desligou@exemplo.com", true, "Desligou");
		profiles.changeEmailDigest("desligou", false);
	}

	void exam(String id, LocalDate end) {
		exams.upsert(NotificationTest.snapshot(id, "SP", null, end, "enfermeiro"), NotificationTest.T1);
	}

	@Test
	void umEmailPorUsuarioComOsConcursosEOsLinks() throws Exception {
		exam("um", LocalDate.of(2026, 11, 30));
		exam("dois", LocalDate.of(2026, 12, 15));

		int sent = job.run();

		// só maria: as outras não confirmaram o e-mail ou desligaram o resumo
		assertThat(sent).isEqualTo(1);
		MimeMessage[] received = smtp.getReceivedMessages();
		assertThat(received).hasSize(1);
		assertThat(received[0].getAllRecipients()[0]).hasToString("maria@exemplo.com");
		assertThat(received[0].getSubject()).isEqualTo("2 concursos novos para você");
		String body = GreenMailUtil.getBody(received[0]);
		assertThat(body).contains("Maria", "Concurso um", "Concurso dois", "Inscri", "30/11/2026",
				"https://app.gabarit.test/concursos/");
	}

	@Test
	void naoMandaODeNovoNoDiaSeguinte() {
		exam("um", LocalDate.of(2026, 11, 30));

		assertThat(job.run()).isEqualTo(1);
		assertThat(job.run()).isZero();
		assertThat(smtp.getReceivedMessages()).hasSize(1);
	}

	@Test
	void avisoMaisVelhoQueAJanelaNaoVaiPorEmail() {
		exam("um", LocalDate.of(2026, 11, 30));
		sql("UPDATE notification SET created_at = created_at - interval '8 days'");

		assertThat(job.run()).isZero();
		assertThat(smtp.getReceivedMessages()).isEmpty();
	}

	@Test
	void concursoQueEncerrouDepoisDoAvisoSaiDoEmail() {
		exam("um", LocalDate.of(2026, 11, 30));
		sql("UPDATE exam SET registration_end = DATE '2026-11-09'");

		assertThat(job.run()).isZero();
		// marcado como enviado mesmo assim: não fica tentando todo dia
		em.flush();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM notification WHERE user_id = 'maria' AND emailed_at IS NULL", Integer.class))
			.isZero();
	}

	@Test
	void avisoDeEditalNaoVaiPorEmail() {
		sql("""
				INSERT INTO study_target (id, user_id, title, created_at, updated_at)
				VALUES ('0193a1f0-0000-7000-8000-0000000000f1', 'maria', 'TJ-SP 2023', now(), now())""");
		sql("""
				INSERT INTO notification (id, user_id, type, study_target_id, title, created_at)
				VALUES (gen_random_uuid(), 'maria', 'EDITAL_READ', '0193a1f0-0000-7000-8000-0000000000f1', 'TJ-SP 2023', now())""");

		assertThat(job.run()).isZero();
		assertThat(smtp.getReceivedMessages()).isEmpty();
	}

}
