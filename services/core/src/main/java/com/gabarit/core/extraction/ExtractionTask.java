package com.gabarit.core.extraction;

/**
 * Tarefas do worker extraction, com a versão de prompt que o core pede. Prompt melhorou no worker
 * → versão nova aqui → o próximo pedido lê de novo (a versão é parte da chave de cache).
 */
public enum ExtractionTask {

	/** Lê os documentos e devolve os parâmetros, sem LLM: testa o caminho core → worker → core. */
	ECHO("v1"),

	/** Edital inteiro → órgão, banca, ano, datas e cargos. */
	EDITAL_OVERVIEW("v1"),

	/** Edital + cargo (params.position: nome e especialidade da EDITAL_OVERVIEW) → disciplinas e tópicos. */
	EDITAL_SYLLABUS("v2");

	private final String promptVersion;

	ExtractionTask(String promptVersion) {
		this.promptVersion = promptVersion;
	}

	public String promptVersion() {
		return promptVersion;
	}

}
