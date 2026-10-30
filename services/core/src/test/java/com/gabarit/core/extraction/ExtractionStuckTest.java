package com.gabarit.core.extraction;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** Leitura parada vira falha que pode ser repetida; o resultado atrasado ainda é aceito (sem banco nem fila). */
class ExtractionStuckTest {

	static final Instant NOW = Instant.parse("2026-11-10T12:00:00Z");

	@Test
	void pendenteHaMuitoTempoViraFalhaQuePodeSerRepetida() {
		ExtractionRepository repository = mock(ExtractionRepository.class);
		ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
		var service = new ExtractionService(repository, mock(RabbitTemplate.class),
				new TransactionTemplate(mock(PlatformTransactionManager.class)), events,
				Clock.fixed(NOW, ZoneOffset.UTC), 150);
		Extraction stuck = new Extraction("maria", ExtractionTask.EDITAL_OVERVIEW, "h", "{}",
				NOW.minus(ExtractionService.STUCK).minusSeconds(60));
		given(repository.findByStatusAndRequestedAtBefore(eq(Extraction.Status.PENDING), any())).willReturn(List.of(stuck));

		service.expireStuck();

		assertThat(stuck.getStatus()).isEqualTo(Extraction.Status.FAILED);
		assertThat(stuck.isRetryable()).isTrue();
		assertThat(stuck.getFailureReason()).contains("não respondeu");
		verify(events).publishEvent(any(ExtractionFinishedEvent.class));
	}

	@Test
	void resultadoAtrasadoAindaEAceito() {
		Extraction late = new Extraction("maria", ExtractionTask.EDITAL_OVERVIEW, "h", "{}", NOW);
		late.fail("o worker não respondeu em 2 h", true, NOW);

		assertThat(late.complete("{}", "claude-haiku-5-5", new Extraction.Usage(10, 10, 0, 0), NOW)).isTrue();
		assertThat(late.getStatus()).isEqualTo(Extraction.Status.DONE);
		assertThat(late.getFailureReason()).isNull();

		// falha definitiva (PDF ilegível): resultado depois não muda nada
		Extraction failed = new Extraction("maria", ExtractionTask.EDITAL_OVERVIEW, "h", "{}", NOW);
		failed.fail("PDF ilegível", false, NOW);
		assertThat(failed.complete("{}", "claude-haiku-5-5", new Extraction.Usage(10, 10, 0, 0), NOW)).isFalse();
		// repetido: ignorado
		assertThat(late.complete("{}", "claude-haiku-5-5", new Extraction.Usage(10, 10, 0, 0), NOW)).isFalse();
	}

}
