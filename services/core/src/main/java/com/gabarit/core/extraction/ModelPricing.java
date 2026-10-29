package com.gabarit.core.extraction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * Preço das leituras em US$, pelos tokens que o worker devolve. Tabela da API do Claude (preços por
 * milhão de tokens, conferidos em 06/10/2026): mudou o preço ou entrou modelo novo, atualize aqui e a
 * V26 (estimativa das leituras antigas). Modelo fora da tabela: custo desconhecido (nulo).
 *
 * <p>O Haiku 5.5 cobra 5× mais quando o prompt passa de 100 mil tokens (entrada + cache): um edital
 * grande, mesmo recortado, pode cair nessa faixa.
 */
final class ModelPricing {

	/** US$ por milhão de tokens. {@code cacheWrite}: gravação de 5 min (1,25× a entrada). */
	record Price(double input, double output, double cacheRead, double cacheWrite) {
	}

	private record Model(Price normal, Price longPrompt, long longPromptAbove) {
	}

	private static final Map<String, Model> MODELS = Map.of(
			// a leitura de cache do Haiku não está na tabela: 0,1× a entrada, como nos outros modelos
			"claude-haiku-5-5", new Model(new Price(0.10, 0.50, 0.01, 0.125), new Price(0.50, 2.50, 0.05, 0.625), 100_000),
			"claude-sonnet-5-5", new Model(new Price(2.00, 10.00, 0.20, 2.50), null, Long.MAX_VALUE),
			"claude-opus-5-5", new Model(new Price(4.00, 20.00, 0.20, 5.00), null, Long.MAX_VALUE));

	private ModelPricing() {
	}

	/** Custo da leitura em US$ (6 casas), ou nulo se o modelo não está na tabela. */
	static BigDecimal cost(String model, Extraction.Usage usage) {
		Model prices = model == null ? null : MODELS.get(model);
		if (prices == null) {
			return null;
		}
		long prompt = (long) usage.inputTokens() + usage.cacheReadInputTokens() + usage.cacheCreationInputTokens();
		Price price = prices.longPrompt() != null && prompt > prices.longPromptAbove() ? prices.longPrompt()
				: prices.normal();
		double dollars = (usage.inputTokens() * price.input() + usage.outputTokens() * price.output()
				+ usage.cacheReadInputTokens() * price.cacheRead()
				+ usage.cacheCreationInputTokens() * price.cacheWrite()) / 1_000_000;
		return BigDecimal.valueOf(dollars).setScale(6, RoundingMode.HALF_UP);
	}

}
