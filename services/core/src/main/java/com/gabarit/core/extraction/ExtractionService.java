package com.gabarit.core.extraction;

import com.gabarit.core.document.DocumentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.gabarit.core.shared.BadRequestException;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Pedidos ao worker extraction (LLM), com cache entre usuários.
 *
 * <p>Fluxo: {@link #request} grava o pedido PENDING e publica extraction.requested; o worker
 * responde com extraction.completed/failed ({@link ExtractionResultListener}), que gravam o
 * resultado e publicam {@link ExtractionFinishedEvent} para quem pediu reagir.
 */
@Service
public class ExtractionService {

	static final String COMMANDS_EXCHANGE = "gabarit.commands";

	/** Pendente por mais que isto: o worker não vai responder (fila longa demora minutos, não horas). */
	static final Duration STUCK = Duration.ofHours(2);

	private static final Logger log = LoggerFactory.getLogger(ExtractionService.class);

	private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private final ExtractionRepository repository;

	private final RabbitTemplate rabbit;

	private final TransactionTemplate transaction;

	private final ApplicationEventPublisher events;

	private final Clock clock;

	private final int dailyLimit;

	ExtractionService(ExtractionRepository repository, RabbitTemplate rabbit, TransactionTemplate transaction,
			ApplicationEventPublisher events, Clock clock, @Value("${gabarit.llm.daily-limit}") int dailyLimit) {
		this.repository = repository;
		this.rabbit = rabbit;
		// transação própria mesmo quando quem pede está numa (ex.: confirmar o envio do edital): o
		// pedido precisa estar gravado antes de publicar, senão a resposta pode chegar antes dele
		this.transaction = new TransactionTemplate(transaction.getTransactionManager());
		this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		this.events = events;
		this.clock = clock;
		this.dailyLimit = dailyLimit;
	}

	/**
	 * Pede uma leitura. Idempotente: se já existe uma com a mesma tarefa, entradas e versão do
	 * prompt, devolve essa (pronta, pendente ou com falha definitiva) sem publicar nada; falha
	 * transitória é pedida de novo. O resultado chega depois, por {@link ExtractionFinishedEvent}.
	 *
	 * <p>Trava de segurança: quem já fez {@code gabarit.llm.daily-limit} pedidos novos em 24 h não
	 * faz mais (leitura que já existe continua vindo).
	 *
	 * @param userId quem pede (guardado na leitura nova: é quem "paga" por ela)
	 * @param documents SHA-256 dos PDFs (blobs do DocumentService), na ordem que a tarefa espera
	 * @param params parâmetros da tarefa (valores JSON: texto, número, lista, mapa...)
	 */
	public Extraction request(String userId, ExtractionTask task, List<String> documents,
			Map<String, Object> params) {
		documents.forEach(sha256 -> {
			if (!SHA256.matcher(sha256).matches()) {
				throw new IllegalArgumentException("sha256 inválido: " + sha256);
			}
		});
		String inputsJson = inputsJson(documents, params);
		String inputsHash = sha256(inputsJson);

		Extraction existing = repository.findByTaskAndInputsHashAndPromptVersion(task, inputsHash, task.promptVersion())
			.orElse(null);
		if (existing != null) {
			return existing.canRetry() ? retry(existing.getId(), documents, params) : existing;
		}

		Instant dayAgo = clock.instant().minus(Duration.ofDays(1));
		if (repository.countByRequestedByAndCreatedAtAfter(userId, dayAgo) >= dailyLimit) {
			throw new BadRequestException(
					"limite de %d leituras novas pela IA em 24 h atingido: tente de novo amanhã".formatted(dailyLimit));
		}
		Extraction created;
		try {
			created = transaction.execute(status -> repository.saveAndFlush(
					new Extraction(userId, task, inputsHash, inputsJson, clock.instant())));
		}
		catch (DataIntegrityViolationException e) {
			// outro pedido igual gravou primeiro (e publica ele): devolve o dele
			return repository.findByTaskAndInputsHashAndPromptVersion(task, inputsHash, task.promptVersion())
				.orElseThrow(() -> e);
		}
		return publish(created, documents, params);
	}

	private Extraction retry(UUID id, List<String> documents, Map<String, Object> params) {
		Extraction retried = transaction.execute(status -> {
			Extraction extraction = repository.findById(id).orElseThrow();
			// outro pedido pode ter reenviado entre a leitura e aqui: aí não publica de novo
			if (!extraction.canRetry()) {
				return null;
			}
			extraction.retry(clock.instant());
			return extraction;
		});
		return retried == null ? repository.findById(id).orElseThrow() : publish(retried, documents, params);
	}

	/**
	 * Publica depois do commit (o resultado pode chegar antes de a transação terminar, e o listener
	 * precisa achar o PENDING). Se o envio falha, marca falha transitória: o próximo pedido reenvia.
	 */
	private Extraction publish(Extraction extraction, List<String> documents, Map<String, Object> params) {
		var command = new ExtractionRequestedCommand(extraction.getId(), extraction.getTask(),
				extraction.getPromptVersion(),
				documents.stream()
					.map(sha256 -> new ExtractionRequestedCommand.Document(sha256, DocumentService.blobKey(sha256)))
					.toList(),
				params);
		try {
			rabbit.convertAndSend(COMMANDS_EXCHANGE, ExtractionRequestedCommand.ROUTING_KEY, command, message -> {
				message.getMessageProperties().setMessageId(UUID.randomUUID().toString());
				message.getMessageProperties().setType(ExtractionRequestedCommand.ROUTING_KEY);
				return message;
			});
		}
		catch (AmqpException e) {
			log.error("extraction.requested {} não enviado", extraction.getId(), e);
			transaction.executeWithoutResult(status -> repository.findById(extraction.getId())
				.ifPresent(pending -> pending.fail("pedido não enviado ao worker: " + e.getMessage(), true, clock.instant())));
			throw e;
		}
		log.info("extraction.requested {} ({})", extraction.getId(), extraction.getTask());
		return extraction;
	}

	/**
	 * A leitura com estas entradas (e o prompt atual), se já existe: para mostrar sem pedir (ex.: o
	 * resumo de um tópico, compartilhado entre todos, sem tabela própria).
	 */
	@Transactional(readOnly = true)
	public Optional<Extraction> lookup(ExtractionTask task, List<String> documents, Map<String, Object> params) {
		return repository.findByTaskAndInputsHashAndPromptVersion(task, sha256(inputsJson(documents, params)),
				task.promptVersion());
	}

	private static String inputsJson(List<String> documents, Map<String, Object> params) {
		return JSON.writeValueAsString(canonical(Map.of("documents", documents, "params", params)));
	}

	/** Leituras por id (id que não existe é ignorado). Uma query. */
	@Transactional(readOnly = true)
	public Map<UUID, Extraction> find(Collection<UUID> ids) {
		return repository.findAllById(ids).stream().collect(Collectors.toMap(Extraction::getId, Function.identity()));
	}

	/** Grava o resultado de um extraction.completed. Repetido ou atrasado: ignorado. */
	@Transactional
	void complete(UUID id, String result, String model, Extraction.Usage usage) {
		repository.findById(id).ifPresentOrElse(extraction -> {
			if (extraction.complete(result, model, usage, clock.instant())) {
				events.publishEvent(new ExtractionFinishedEvent(id, extraction.getTask(), extraction.getStatus()));
			}
		}, () -> log.warn("extraction.completed de pedido desconhecido: {}", id));
	}

	/**
	 * A cada 10 min: leitura pendente há mais de {@link #STUCK} vira falha que pode ser repetida
	 * (worker parado, mensagem perdida na DLQ). Quem pediu vê "tentar de novo"; se o resultado chegar
	 * depois, ainda é aceito ({@link Extraction#complete}).
	 */
	@Scheduled(fixedDelay = 10, initialDelay = 10, timeUnit = TimeUnit.MINUTES)
	@Transactional
	public void expireStuck() {
		Instant now = clock.instant();
		for (Extraction extraction : repository.findByStatusAndRequestedAtBefore(Extraction.Status.PENDING,
				now.minus(STUCK))) {
			if (extraction.fail("o worker não respondeu em %d h".formatted(STUCK.toHours()), true, now)) {
				log.warn("extraction {} ({}) parada desde {}: dada como falha", extraction.getId(), extraction.getTask(),
						extraction.getRequestedAt());
				events.publishEvent(new ExtractionFinishedEvent(extraction.getId(), extraction.getTask(),
						extraction.getStatus()));
			}
		}
	}

	/** Grava um extraction.failed. Repetido ou atrasado: ignorado. */
	@Transactional
	void fail(UUID id, String reason, boolean retryable) {
		repository.findById(id).ifPresentOrElse(extraction -> {
			if (extraction.fail(reason, retryable, clock.instant())) {
				log.warn("extraction {} falhou (retryable={}): {}", id, retryable, reason);
				events.publishEvent(new ExtractionFinishedEvent(id, extraction.getTask(), extraction.getStatus()));
			}
		}, () -> log.warn("extraction.failed de pedido desconhecido: {}", id));
	}

	/**
	 * Mesmas entradas → mesmo JSON → mesmo hash: mapas (em qualquer nível) com as chaves em ordem
	 * alfabética. Listas mantêm a ordem (a ordem dos documentos importa para a tarefa).
	 */
	private static Object canonical(Object value) {
		return switch (value) {
			case Map<?, ?> map -> {
				Map<String, Object> sorted = new TreeMap<>();
				map.forEach((key, item) -> sorted.put(String.valueOf(key), canonical(item)));
				yield sorted;
			}
			case List<?> list -> list.stream().map(ExtractionService::canonical).toList();
			case null, default -> value;
		};
	}

	private static String sha256(String json) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(json.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

}
