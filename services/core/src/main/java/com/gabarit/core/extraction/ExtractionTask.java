package com.gabarit.core.extraction;

/**
 * Tarefas do worker extraction, com a versão de prompt que o core pede. Prompt melhorou no worker
 * → versão nova aqui → o próximo pedido lê de novo (a versão é parte da chave de cache).
 */
public enum ExtractionTask {

	/** Lê os documentos e devolve os parâmetros, sem LLM: testa o caminho core → worker → core. */
	ECHO("v1"),

	/** Edital inteiro → órgão, banca, ano, datas, cargos e (v2) o quadro de provas. */
	EDITAL_OVERVIEW("v2"),

	/** Edital + cargo (params.position: nome e especialidade da EDITAL_OVERVIEW) → disciplinas e tópicos. */
	EDITAL_SYLLABUS("v2"),

	/** Prova (+ gabarito, se separado; params.variant: tipo/cor) → questões, alternativas, resposta, anuladas. */
	EXAM_PAPER("v1"),

	/** Sem documento: questões já lidas + tópicos do conteúdo de um cargo (params) → o tópico de cada questão. */
	QUESTION_TOPICS("v1"),

	/** Sem documento: tópico + banca + formato (params) → questões inéditas no estilo da banca, com comentário. */
	TOPIC_QUESTIONS("v1"),

	/** Sem documento: disciplina + tópico (params) → resumo, o que mais cai, pegadinhas, leis e leituras. */
	TOPIC_SUMMARY("v1");

	private final String promptVersion;

	ExtractionTask(String promptVersion) {
		this.promptVersion = promptVersion;
	}

	public String promptVersion() {
		return promptVersion;
	}

}
