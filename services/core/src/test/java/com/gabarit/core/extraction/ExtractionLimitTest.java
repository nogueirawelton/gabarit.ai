package com.gabarit.core.extraction;

import com.gabarit.core.shared.BadRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/** Trava diária de pedidos novos à LLM e o custo pela tabela de preços (sem banco nem fila). */
class ExtractionLimitTest {

	static final String SHA = "a".repeat(64);

	@Test
	void quemPassouDoLimiteNaoPedeLeituraNova() {
		ExtractionRepository repository = mock(ExtractionRepository.class);
		RabbitTemplate rabbit = mock(RabbitTemplate.class);
		TransactionTemplate transaction = new TransactionTemplate(mock(PlatformTransactionManager.class));
		Clock clock = Clock.fixed(Instant.parse("2026-11-10T12:00:00Z"), ZoneOffset.UTC);
		var service = new ExtractionService(repository, rabbit, transaction, mock(ApplicationEventPublisher.class), clock, 2);
		given(repository.findByTaskAndInputsHashAndPromptVersion(any(), anyString(), anyString())).willReturn(Optional.empty());
		given(repository.countByRequestedByAndCreatedAtAfter(eq("maria"), any())).willReturn(2L);

		assertThatThrownBy(() -> service.request("maria", ExtractionTask.ECHO, List.of(SHA), Map.of()))
			.isInstanceOf(BadRequestException.class);
		verifyNoInteractions(rabbit);
	}

	@Test
	void custoPelaTabelaComAFaixaDePromptLongoDoHaiku() {
		// 50 mil de entrada e 5 mil de saída no Haiku: 0,005 + 0,0025
		assertThat(ModelPricing.cost("claude-haiku-5-5", new Extraction.Usage(50_000, 5_000, 0, 0)))
			.isEqualByComparingTo(new BigDecimal("0.0075"));
		// acima de 100 mil de prompt, 5× mais: 0,0675 + 0,0125
		assertThat(ModelPricing.cost("claude-haiku-5-5", new Extraction.Usage(135_000, 5_000, 0, 0)))
			.isEqualByComparingTo(new BigDecimal("0.08"));
		assertThat(ModelPricing.cost("claude-opus-5-5", new Extraction.Usage(100_000, 10_000, 0, 0)))
			.isEqualByComparingTo(new BigDecimal("0.6"));
		assertThat(ModelPricing.cost("modelo-novo", new Extraction.Usage(1, 1, 0, 0))).isNull();
	}

}
