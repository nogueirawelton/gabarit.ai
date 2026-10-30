package com.gabarit.core.notification;

import com.gabarit.core.PostgresTestConfiguration;
import com.gabarit.core.exam.CatalogPosition;
import com.gabarit.core.exam.CatalogPositionService;
import com.gabarit.core.exam.EducationLevel;
import com.gabarit.core.exam.ExamFilter;
import com.gabarit.core.exam.ExamService;
import com.gabarit.core.exam.ExamSnapshot;
import com.gabarit.core.exam.ExamStatus;
import com.gabarit.core.exam.ExamSummary;
import com.gabarit.core.shared.ClockConfig;
import com.gabarit.core.shared.NotFoundException;
import com.gabarit.core.study.EditalReadEvent;
import com.gabarit.core.study.StudyReadyEvent;
import com.gabarit.core.user.UserProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Concurso novo vira aviso para quem se interessa: Postgres real, "hoje" fixo em 10/11/2026. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ PostgresTestConfiguration.class, ExamService.class, CatalogPositionService.class, UserProfileService.class,
		NotificationService.class, NotificationTest.FixedClock.class })
class NotificationTest {

	static final LocalDate TODAY = LocalDate.of(2026, 11, 10);

	static final Instant T1 = Instant.parse("2026-11-01T09:00:00Z");

	@TestConfiguration(proxyBeanMethods = false)
	static class FixedClock {

		@Bean
		Clock clock() {
			return Clock.fixed(ZonedDateTime.of(TODAY.atTime(12, 0), ClockConfig.ZONE).toInstant(), ClockConfig.ZONE);
		}

	}

	@Autowired
	ExamService exams;

	@Autowired
	CatalogPositionService catalog;

	@Autowired
	UserProfileService profiles;

	@Autowired
	NotificationService notifications;

	@Autowired
	NotificationRepository repository;

	@Autowired
	TestEntityManager em;

	@Autowired
	JdbcTemplate jdbc;

	Map<String, UUID> catalogIds;

	@BeforeEach
	void perfis() {
		catalog.sync("pciconcursos", List.of(new CatalogPositionService.Entry("enfermeiro", "Enfermeiro"),
				new CatalogPositionService.Entry("analista", "Analista")), T1);
		catalogIds = catalog.list("pciconcursos")
			.stream()
			.collect(Collectors.toMap(CatalogPosition::getSlug, CatalogPosition::getId));
		profile("maria", List.of("enfermeiro"), List.of("SP"), List.of());
		profile("joao", List.of("enfermeiro"), List.of(), List.of(EducationLevel.MEDIO));
		profile("ana", List.of("analista"), List.of(), List.of());
		profile("bia", List.of("enfermeiro"), List.of("RJ"), List.of());
		profile("sem-cargo", List.of(), List.of(), List.of());
	}

	void profile(String userId, List<String> slugs, List<String> ufs, List<EducationLevel> levels) {
		profiles.update(userId, slugs.stream().map(catalogIds::get).toList(), ufs, levels);
	}

	UUID exam(String id, String uf, String educationLevel, LocalDate end, String... slugs) {
		exams.upsert(snapshot(id, uf, educationLevel, end, slugs), T1);
		return exams.search(new ExamFilter(null, null, null, "Concurso " + id, null), 0, 1)
			.getContent()
			.getFirst()
			.id();
	}

	static ExamSnapshot snapshot(String id, String uf, String educationLevel, LocalDate end, String... slugs) {
		return new ExamSnapshot("pciconcursos", id, "Concurso " + id, "https://example.com/" + id, uf, null,
				educationLevel, null, null, end, null,
				Arrays.stream(slugs).map(slug -> new ExamSnapshot.Position("Cargo " + slug, List.of(slug))).toList());
	}

	Set<String> notified(UUID examId) {
		return repository.findAll()
			.stream()
			.filter(n -> n.getExamId().equals(examId))
			.map(Notification::getUserId)
			.collect(Collectors.toSet());
	}

	@Test
	void concursoNovoAvisaSoQuemSegueOCargoNaUfENaEscolaridade() {
		UUID sp = exam("sp", "SP", "Superior", LocalDate.of(2026, 11, 30), "enfermeiro");
		UUID nacional = exam("nacional", null, "Médio / Superior", LocalDate.of(2026, 11, 30), "enfermeiro");
		UUID analista = exam("analista", "BA", null, LocalDate.of(2026, 11, 30), "analista");

		// joao quer nível médio (o de SP é só superior); bia quer RJ
		assertThat(notified(sp)).containsExactly("maria");
		// nacional vale para qualquer UF; tem nível médio
		assertThat(notified(nacional)).containsExactlyInAnyOrder("maria", "joao", "bia");
		assertThat(notified(analista)).containsExactly("ana");
	}

	@Test
	void concursoQueJaChegaEncerradoNaoAvisa() {
		UUID closed = exam("velho", "SP", null, LocalDate.of(2026, 11, 1), "enfermeiro");

		assertThat(notified(closed)).isEmpty();
	}

	@Test
	void reentregaEAtualizacaoNaoAvisamDeNovo() {
		UUID id = exam("sp", "SP", null, LocalDate.of(2026, 11, 30), "enfermeiro");
		// maria (SP) e joao (concurso sem escolaridade informada vale para qualquer nível)
		assertThat(notified(id)).containsExactlyInAnyOrder("maria", "joao");

		exams.upsert(snapshot("sp", "SP", null, LocalDate.of(2026, 11, 30), "enfermeiro"), T1);
		exams.upsert(snapshot("sp", "SP", null, LocalDate.of(2026, 12, 5), "enfermeiro"), T1.plusSeconds(60));

		assertThat(repository.findAll()).filteredOn(n -> n.getExamId().equals(id)).hasSize(2);
	}

	/** O aviso tem que ir exatamente para quem veria o concurso em "meus concursos" (mesmas regras). */
	@Test
	void avisadosSaoOsMesmosQueVeemOConcursoEmMeusConcursos() {
		List<UUID> ids = List.of(exam("a", "SP", "Superior", LocalDate.of(2026, 11, 30), "enfermeiro"),
				exam("b", null, "Médio", LocalDate.of(2026, 11, 30), "enfermeiro", "analista"),
				exam("c", "RJ", null, null, "enfermeiro"), exam("d", "MG", "Fundamental", null, "analista"));

		for (String userId : List.of("maria", "joao", "ana", "bia", "sem-cargo")) {
			var profile = profiles.get(userId);
			Set<UUID> visible = profile.positions().isEmpty() ? Set.of()
					: exams.search(new ExamFilter(new HashSet<>(profile.ufs()),
							EnumSet.of(ExamStatus.OPEN, ExamStatus.UPCOMING, ExamStatus.UNKNOWN),
							new HashSet<>(profile.educationLevels()), null,
							Map.of("pciconcursos",
									profile.positions().stream().map(CatalogPosition::getSlug).collect(Collectors.toSet()))),
							0, 50)
						.stream()
						.map(ExamSummary::id)
						.collect(Collectors.toSet());
			Set<UUID> got = ids.stream().filter(id -> notified(id).contains(userId)).collect(Collectors.toSet());

			assertThat(got).as(userId).isEqualTo(visible);
		}
	}

	@Test
	void listaContaEMarcaComoLido() {
		UUID first = exam("um", "SP", null, LocalDate.of(2026, 11, 30), "enfermeiro");
		UUID second = exam("dois", "SP", null, LocalDate.of(2026, 11, 30), "enfermeiro");
		em.flush();
		em.clear();

		var page = notifications.list("maria", 0, 20);
		assertThat(page.getContent()).extracting(n -> n.exam().id()).containsExactlyInAnyOrder(first, second);
		assertThat(page.getContent()).noneMatch(NotificationService.NotificationView::read);
		assertThat(notifications.unreadCount("maria")).isEqualTo(2);

		notifications.markRead("maria", page.getContent().getFirst().id());
		assertThat(notifications.unreadCount("maria")).isEqualTo(1);

		notifications.markAllRead("maria");
		em.flush();
		em.clear();
		assertThat(notifications.unreadCount("maria")).isZero();
	}

	@Test
	void naoMarcaAvisoDeOutroUsuario() {
		exam("um", "SP", null, LocalDate.of(2026, 11, 30), "enfermeiro");
		UUID id = notifications.list("maria", 0, 1).getContent().getFirst().id();

		assertThatThrownBy(() -> notifications.markRead("joao", id)).isInstanceOf(NotFoundException.class);
	}

	@Test
	void leituraDoEditalViraAvisoComOConcursoDaCarteira() {
		UUID target = UUID.randomUUID();
		jdbc.update("INSERT INTO study_target (id, user_id, title, created_at, updated_at) VALUES (?, 'maria', 'TJ-SP 2023', now(), now())",
				target);

		notifications.onEditalRead(new EditalReadEvent("maria", target, "TJ-SP 2023", true));
		notifications.onEditalRead(new EditalReadEvent("maria", target, "TJ-SP 2023", false));
		em.flush();
		em.clear();

		var page = notifications.list("maria", 0, 20);
		assertThat(page.getContent()).extracting(NotificationService.NotificationView::type)
			.containsExactlyInAnyOrder(Notification.Type.EDITAL_READ, Notification.Type.EDITAL_FAILED);
		assertThat(page.getContent()).allSatisfy(n -> {
			assertThat(n.exam()).isNull();
			assertThat(n.studyTarget()).isEqualTo(new NotificationService.StudyTargetRef(target, "TJ-SP 2023"));
		});
		assertThat(notifications.unreadCount("maria")).isEqualTo(2);
	}

	@Test
	void outrasLeiturasViramAvisoDoConcurso() {
		UUID target = UUID.randomUUID();
		jdbc.update("INSERT INTO study_target (id, user_id, title, created_at, updated_at) VALUES (?, 'maria', 'TJ-SP 2023', now(), now())",
				target);

		notifications.onStudyReady(new StudyReadyEvent("maria", target, "TJ-SP 2023", StudyReadyEvent.What.SYLLABUS, true));
		notifications.onStudyReady(new StudyReadyEvent("maria", target, "TJ-SP 2023", StudyReadyEvent.What.PAPER, false));
		notifications.onStudyReady(new StudyReadyEvent("maria", target, "TJ-SP 2023", StudyReadyEvent.What.QUESTIONS, true));
		em.flush();
		em.clear();

		assertThat(notifications.list("maria", 0, 20).getContent()).extracting(NotificationService.NotificationView::type)
			.containsExactlyInAnyOrder(Notification.Type.SYLLABUS_READ, Notification.Type.PAPER_FAILED,
					Notification.Type.QUESTIONS_READY);
	}

}
