package com.gabarit.core.exam;

import com.gabarit.core.PostgresTestConfiguration;
import com.gabarit.core.shared.ClockConfig;
import com.gabarit.core.shared.NotFoundException;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Busca no Postgres real, com "hoje" fixo em 10/11/2026 (fuso de Brasília). */
@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ PostgresTestConfiguration.class, ExamService.class, ExamSearchTest.FixedClock.class })
class ExamSearchTest {

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
	ExamService service;

	@Autowired
	TestEntityManager em;

	@Autowired
	EntityManagerFactory emf;

	record Position(String name, String... slugs) {
	}

	static Position enfermeiro() {
		return new Position("Enfermeiro", "enfermeiro");
	}

	void exam(String id, String title, String uf, LocalDate start, LocalDate end, Position... positions) {
		service.upsert(snapshot(id, title, uf, start, end, positions), T1);
	}

	static ExamSnapshot snapshot(String id, String title, String uf, LocalDate start, LocalDate end,
			Position... positions) {
		return new ExamSnapshot("pciconcursos", id, title, "https://example.com/" + id, uf, null, null, null, start, end,
				null, Arrays.stream(positions).map(p -> new ExamSnapshot.Position(p.name(), List.of(p.slugs()))).toList());
	}

	static LocalDate nov(int day) {
		return LocalDate.of(2026, 11, day);
	}

	@BeforeEach
	void concursos() {
		exam("aberto", "Prefeitura de São Paulo - SP", "SP", null, nov(20), enfermeiro());
		exam("abre-hoje", "Câmara de BH - MG", "MG", nov(10), LocalDate.of(2026, 12, 1),
				new Position("Analista", "analista"));
		exam("fecha-hoje", "Técnico 100% remoto - RJ", "RJ", null, nov(10), new Position("Técnico", "tecnico"));
		exam("futuro", "Hospital de Campinas - SP", "SP", nov(11), LocalDate.of(2026, 12, 20), enfermeiro());
		exam("encerrado", "Prefeitura de Santos - SP", "SP", null, nov(9), enfermeiro());
		exam("sem-datas", "Ministério da Saúde", null, null, null, enfermeiro());
		exam("sem-fim", "Prefeitura de Salvador - BA", "BA", nov(1), null, new Position("Motorista", "motorista"));
		// segue enfermeiro, mas o cargo saiu do concurso (inativo): não conta
		exam("cargo-inativo", "Prefeitura de Sorocaba - SP", "SP", null, nov(25), enfermeiro(),
				new Position("Motorista", "motorista"));
		service.upsert(snapshot("cargo-inativo", "Prefeitura de Sorocaba - SP", "SP", null, nov(25),
				new Position("Motorista", "motorista")), T1.plusSeconds(60));
		// a busca tem que ir ao banco, não ao cache da sessão
		em.flush();
		em.clear();
	}

	List<String> ids(ExamFilter filter) {
		return externalIds(service.search(filter, 0, 50).getContent());
	}

	List<String> externalIds(List<ExamSummary> exams) {
		return exams.stream().map(e -> e.url().substring("https://example.com/".length())).toList();
	}

	@Test
	void statusCalculadoNasBordasDasDatas() {
		Map<String, ExamStatus> status = service.search(ExamFilter.all(), 0, 50)
			.getContent()
			.stream()
			.collect(Collectors.toMap(e -> e.url().substring("https://example.com/".length()), ExamSummary::status));

		assertThat(status).containsEntry("aberto", ExamStatus.OPEN)
			.containsEntry("abre-hoje", ExamStatus.OPEN)
			.containsEntry("fecha-hoje", ExamStatus.OPEN)
			.containsEntry("sem-fim", ExamStatus.OPEN)
			.containsEntry("futuro", ExamStatus.UPCOMING)
			.containsEntry("encerrado", ExamStatus.CLOSED)
			.containsEntry("sem-datas", ExamStatus.UNKNOWN);
	}

	@Test
	void filtroDeStatusNoBancoBateComOCalculadoEmJava() {
		for (ExamStatus status : ExamStatus.values()) {
			List<ExamSummary> found = service.search(new ExamFilter(null, Set.of(status), null, null, null), 0, 50)
				.getContent();

			assertThat(found).as(status.name()).isNotEmpty().allMatch(e -> e.status() == status);
		}
		var openOrUpcoming = new ExamFilter(null, EnumSet.of(ExamStatus.OPEN, ExamStatus.UPCOMING), null, null, null);
		assertThat(ids(openOrUpcoming)).hasSize(6).doesNotContain("encerrado", "sem-datas");
	}

	@Test
	void abertosPrimeiroPeloFimMaisProximoESemFimNoFinalDoGrupo() {
		assertThat(ids(ExamFilter.all())).containsExactly("fecha-hoje", "aberto", "cargo-inativo", "abre-hoje", "sem-fim",
				"futuro", "sem-datas", "encerrado");
	}

	@Test
	void filtroDeUfIncluiOsNacionais() {
		assertThat(ids(new ExamFilter(Set.of("sp"), null, null, null, null))).containsExactlyInAnyOrder("aberto", "futuro",
				"encerrado", "cargo-inativo", "sem-datas");
	}

	@Test
	void textoIgnoraCaixaETrataCuringaComoTexto() {
		assertThat(ids(new ExamFilter(null, null, null, "SÃO PAULO", null))).containsExactly("aberto");
		assertThat(ids(new ExamFilter(null, null, null, "100%", null))).containsExactly("fecha-hoje");
		assertThat(ids(new ExamFilter(null, null, null, "_", null))).isEmpty();
	}

	@Test
	void buscaIgnoraAcento() {
		assertThat(ids(new ExamFilter(null, null, null, "tecnico", null))).containsExactly("fecha-hoje");
		assertThat(ids(new ExamFilter(null, null, null, "SAO PAULO", null))).containsExactly("aberto");
		assertThat(ids(new ExamFilter(null, null, null, "camara de bh", null))).containsExactly("abre-hoje");
	}

	@Test
	void escolaridadeComAlgumNivelEmComumOuSemEscolaridadeInformada() {
		service.upsert(withEducation("medio-superior", "Médio / Superior"), T1);
		service.upsert(withEducation("so-tecnico", "Ensino Técnico"), T1);
		service.upsert(withEducation("fundamental", "Fundamental"), T1);
		em.flush();
		em.clear();
		var filter = new ExamFilter(null, null, Set.of(EducationLevel.MEDIO, EducationLevel.TECNICO), "Escola", null);

		// os concursos do @BeforeEach não têm escolaridade: entram sempre, mas não têm "Escola" no título
		assertThat(ids(filter)).containsExactlyInAnyOrder("medio-superior", "so-tecnico");
		assertThat(ids(new ExamFilter(null, null, Set.of(EducationLevel.SUPERIOR), null, null))).contains("aberto",
				"medio-superior").doesNotContain("so-tecnico", "fundamental");
	}

	static ExamSnapshot withEducation(String id, String educationLevel) {
		return new ExamSnapshot("pciconcursos", id, "Escola " + id, "https://example.com/" + id, "SP", null,
				educationLevel, null, null, nov(20), null, List.of());
	}

	@Test
	void cargosSeguidosSoContamAtivosEDaMesmaFonte() {
		var follows = new ExamFilter(null, null, null, null, Map.of("pciconcursos", Set.of("enfermeiro", "analista")));

		List<ExamSummary> found = service.search(follows, 0, 50).getContent();

		assertThat(externalIds(found)).containsExactly("aberto", "abre-hoje", "futuro", "sem-datas", "encerrado");
		assertThat(found).filteredOn(e -> e.title().contains("São Paulo"))
			.singleElement()
			.extracting(ExamSummary::matchingPositions)
			.isEqualTo(List.of("Enfermeiro"));
		assertThat(ids(new ExamFilter(null, null, null, null, Map.of("outra-fonte", Set.of("enfermeiro"))))).isEmpty();
		assertThat(ids(new ExamFilter(null, null, null, null, Map.of()))).isEmpty();
	}

	@Test
	void filtrosCombinados() {
		var filter = new ExamFilter(Set.of("SP"), Set.of(ExamStatus.OPEN, ExamStatus.UPCOMING), null, null,
				Map.of("pciconcursos", Set.of("enfermeiro")));

		assertThat(ids(filter)).containsExactly("aberto", "futuro");
	}

	@Test
	void paginacao() {
		Page<ExamSummary> second = service.search(ExamFilter.all(), 1, 3);

		assertThat(externalIds(second.getContent())).containsExactly("abre-hoje", "sem-fim", "futuro");
		assertThat(second.getTotalElements()).isEqualTo(8);
		assertThat(second.getTotalPages()).isEqualTo(3);
	}

	@Test
	void numeroDeQueriesNaoDependeDoTamanhoDaPagina() {
		IntStream.range(0, 30)
			.forEach(i -> exam("lote-" + i, "Concurso " + i, "SP", null, nov(28), enfermeiro(),
					new Position("Analista", "analista"), new Position("Motorista", "motorista")));
		em.flush();
		em.clear();
		var follows = new ExamFilter(null, null, null, null, Map.of("pciconcursos", Set.of("enfermeiro")));

		long small = queries(() -> service.search(follows, 0, 5));
		long large = queries(() -> service.search(follows, 0, 30));

		// contagem + página + cargos seguidos da página
		assertThat(small).isEqualTo(3);
		assertThat(large).isEqualTo(small);
	}

	long queries(Runnable search) {
		Statistics statistics = emf.unwrap(SessionFactory.class).getStatistics();
		em.clear();
		statistics.clear();
		search.run();
		return statistics.getPrepareStatementCount();
	}

	@Test
	void detalheTrazSoOsCargosAtivosEmOrdemAlfabetica() {
		UUID id = service.search(new ExamFilter(null, null, null, "Sorocaba", null), 0, 1).getContent().getFirst().id();
		exam("varios", "Varios cargos", "SP", null, nov(20), new Position("Técnico"), new Position("analista"),
				new Position("Ácido"));
		em.flush();
		em.clear();
		UUID varios = service.search(new ExamFilter(null, null, null, "Varios", null), 0, 1).getContent().getFirst().id();

		assertThat(service.get(id).positions()).extracting(ExamDetail.Position::name).containsExactly("Motorista");
		assertThat(service.get(varios).positions()).extracting(ExamDetail.Position::name)
			.containsExactly("Ácido", "analista", "Técnico");
		assertThat(service.get(id).status()).isEqualTo(ExamStatus.OPEN);
	}

	@Test
	void detalheDeConcursoQueNaoExisteE404() {
		assertThatThrownBy(() -> service.get(UUID.randomUUID())).isInstanceOf(NotFoundException.class);
	}

}
