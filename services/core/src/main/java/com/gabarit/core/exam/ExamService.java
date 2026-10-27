package com.gabarit.core.exam;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** Porta de entrada do pacote exam: outros pacotes usam isto, nunca o repositório. */
@Service
public class ExamService {

	public enum UpsertResult {

		CREATED, UPDATED,
		/** Evento mais antigo (ou repetido) que o último aplicado. */
		IGNORED

	}

	private final ExamRepository repository;

	ExamService(ExamRepository repository) {
		this.repository = repository;
	}

	/**
	 * Cria ou atualiza o concurso pela identidade (source, externalId). Idempotente: o mesmo
	 * evento entregue de novo não muda nada.
	 *
	 * @throws IllegalArgumentException se os dados do concurso são inválidos (ex.: datas)
	 */
	@Transactional
	public UpsertResult upsert(ExamSnapshot snapshot, Instant occurredAt) {
		return repository.findBySourceAndExternalId(snapshot.source(), snapshot.externalId())
				.map(exam -> exam.apply(snapshot, occurredAt) ? UpsertResult.UPDATED : UpsertResult.IGNORED)
				.orElseGet(() -> {
					// dois consumidores criando o mesmo concurso ao mesmo tempo: o segundo viola a
					// unique, a mensagem volta pra fila e, na reentrega, cai no apply acima
					repository.save(Exam.create(snapshot, occurredAt));
					return UpsertResult.CREATED;
				});
	}

}
