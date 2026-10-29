package com.gabarit.core.exam;

import java.text.Normalizer;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/** Nível de escolaridade exigido pelas vagas de um concurso (um concurso costuma ter vários). */
public enum EducationLevel {

	FUNDAMENTAL, MEDIO, TECNICO, SUPERIOR;

	/**
	 * Níveis do texto livre da fonte: "Fundamental / Médio / Superior", "Ensino Técnico"... Ignora
	 * acento e caixa; o que não reconhece fica de fora (nulo ou vazio = nenhum nível). A V6 tem a
	 * mesma regra em SQL para os concursos que já estavam no banco.
	 */
	static Set<EducationLevel> parse(String text) {
		Set<EducationLevel> levels = EnumSet.noneOf(EducationLevel.class);
		if (text == null) {
			return levels;
		}
		String normalized = Normalizer.normalize(text, Normalizer.Form.NFD)
			.replaceAll("\\p{M}", "")
			.toLowerCase(Locale.ROOT);
		if (normalized.contains("fundamental")) {
			levels.add(FUNDAMENTAL);
		}
		if (normalized.contains("medio")) {
			levels.add(MEDIO);
		}
		if (normalized.contains("tecnico")) {
			levels.add(TECNICO);
		}
		if (normalized.contains("superior")) {
			levels.add(SUPERIOR);
		}
		return levels;
	}

}
