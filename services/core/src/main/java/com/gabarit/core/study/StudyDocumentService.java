package com.gabarit.core.study;

import com.gabarit.core.document.DocumentService;
import com.gabarit.core.extraction.Extraction;
import com.gabarit.core.extraction.ExtractionService;
import com.gabarit.core.extraction.ExtractionTask;
import com.gabarit.core.shared.BadRequestException;
import com.gabarit.core.shared.NotFoundException;
import com.gabarit.core.study.StudyDocument.Kind;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Materiais de um concurso da carteira: editais e provas relacionadas (cada uma com o espaço da prova
 * e o do gabarito). Envio em três passos: o core entrega a URL, o navegador envia direto ao MinIO e
 * confirma, e o core confere o arquivo (pacote document).
 */
@Service
public class StudyDocumentService {

	private static final Logger log = LoggerFactory.getLogger(StudyDocumentService.class);

	/** Arquivos por concurso (editais + retificações + provas e gabaritos de vários anos cabem com folga). */
	public static final int MAX_DOCUMENTS_PER_TARGET = 30;

	/** Provas relacionadas por concurso. */
	public static final int MAX_PAPERS_PER_TARGET = 12;

	/** Envios por usuário em 24h: contém abuso (cada arquivo, depois, custa leitura da LLM). */
	public static final int MAX_UPLOADS_PER_DAY = 50;

	/**
	 * Arquivo. {@code url}: para abrir o PDF (só aprovados, expira em 1h). {@code reading}: a leitura
	 * do edital pela LLM (só no edital do concurso-alvo; nula se nunca foi pedida). {@code position}:
	 * o cargo escolhido entre os da leitura (nulo se nenhum); {@code syllabus}: a leitura do conteúdo
	 * programático desse cargo.
	 */
	public record DocumentView(UUID id, Kind kind, String originalName, StudyDocument.Status status,
			String rejectionReason, Long sizeBytes, Integer pages, Instant createdAt, URI url, Reading reading,
			Position position, SyllabusReading syllabus) {
	}

	/** Leitura do conteúdo programático do cargo: mesmos estados da {@link Reading}. */
	public record SyllabusReading(Extraction.Status status, EditalSyllabus syllabus, String failureReason,
			boolean retryable) {

		static SyllabusReading of(Extraction extraction) {
			return new SyllabusReading(extraction.getStatus(),
					extraction.getStatus() == Extraction.Status.DONE ? EditalSyllabus.parse(extraction.getResult()) : null,
					extraction.getFailureReason(), extraction.isRetryable());
		}

	}

	/** Cargo como o edital escreve; {@code specialty} nula = cargo sem áreas. */
	public record Position(String name, String specialty) {
	}

	/**
	 * Leitura do edital: {@code PENDING} (na fila ou lendo) → {@code DONE} com a visão geral, ou
	 * {@code FAILED} com o motivo ({@code retryable}: pedir de novo pode dar certo).
	 */
	public record Reading(Extraction.Status status, EditalOverview overview, String failureReason,
			boolean retryable) {

		static Reading of(Extraction extraction) {
			return new Reading(extraction.getStatus(),
					extraction.getStatus() == Extraction.Status.DONE ? EditalOverview.parse(extraction.getResult()) : null,
					extraction.getFailureReason(), extraction.isRetryable());
		}

	}

	/**
	 * Prova relacionada com os espaços; vazio = nulo. Com {@code answersIncluded} não há gabarito separado.
	 * {@code edital}: o edital daquela prova (opcional; sem ele, vale o edital do concurso-alvo).
	 */
	public record PaperView(UUID id, Integer year, String variant, boolean answersIncluded, Instant createdAt,
			DocumentView exam, DocumentView answerKey, DocumentView edital) {
	}

	/** Tudo do concurso, para a página de estudo. {@code editais}: os do concurso-alvo (fora das provas). */
	public record Materials(List<DocumentView> editais, List<PaperView> papers) {
	}

	/** Envio começado: o navegador faz PUT em {@code uploadUrl} com {@code contentType} e depois confirma. */
	public record UploadStarted(DocumentView document, URI uploadUrl, String contentType) {
	}

	private final StudyDocumentRepository repository;

	private final StudyPaperRepository papers;

	private final StudyTargetRepository targets;

	private final DocumentService documents;

	private final ExtractionService extractions;

	private final Clock clock;

	StudyDocumentService(StudyDocumentRepository repository, StudyPaperRepository papers,
			StudyTargetRepository targets, DocumentService documents, ExtractionService extractions, Clock clock) {
		this.repository = repository;
		this.papers = papers;
		this.targets = targets;
		this.documents = documents;
		this.extractions = extractions;
		this.clock = clock;
	}

	/** Editais e provas relacionadas, na ordem em que foram adicionados. Número fixo de queries. */
	@Transactional(readOnly = true)
	public Materials materials(String userId, UUID targetId) {
		checkTarget(userId, targetId);
		List<StudyDocument> all = repository.findByStudyTargetIdOrderByCreatedAtAscIdAsc(targetId);
		Map<UUID, Extraction> readings = extractions.find(all.stream()
			.flatMap(d -> Stream.of(d.getExtractionId(), d.getSyllabusExtractionId()))
			.filter(Objects::nonNull)
			.toList());
		Map<UUID, Map<Kind, StudyDocument>> byPaper = all.stream()
			.filter(d -> d.getPaperId() != null)
			.collect(Collectors.groupingBy(StudyDocument::getPaperId,
					Collectors.toMap(StudyDocument::getKind, Function.identity())));
		List<DocumentView> editais = all.stream()
			.filter(StudyDocument::isTargetEdital)
			.map(d -> view(d, readings))
			.toList();
		List<PaperView> paperViews = papers.findByStudyTargetIdOrderByCreatedAtAscIdAsc(targetId)
			.stream()
			.map(p -> paperView(p, byPaper.getOrDefault(p.getId(), Map.of())))
			.toList();
		return new Materials(editais, paperViews);
	}

	/**
	 * Nova prova relacionada, ainda sem arquivos.
	 *
	 * @throws BadRequestException limite de provas do concurso
	 */
	@Transactional
	public PaperView addPaper(String userId, UUID targetId, Integer year, String variant, boolean answersIncluded) {
		checkTarget(userId, targetId);
		if (papers.countByStudyTargetId(targetId) >= MAX_PAPERS_PER_TARGET) {
			throw new BadRequestException(
					"este concurso já tem %d provas: remova alguma para adicionar outra".formatted(MAX_PAPERS_PER_TARGET));
		}
		StudyPaper paper = papers.save(new StudyPaper(userId, targetId, year, blankToNull(variant), answersIncluded));
		return paperView(paper, Map.of());
	}

	/** Remove a prova relacionada com os arquivos dela (os blobs continuam: podem ser de outra pessoa). */
	@Transactional
	public void removePaper(String userId, UUID targetId, UUID paperId) {
		papers.delete(findPaper(userId, targetId, paperId));
	}

	/**
	 * Começa um envio. Edital do concurso-alvo: {@code paperId} nulo. Prova, gabarito e edital daquela
	 * prova: o espaço da prova relacionada {@code paperId}, que tem de estar livre (um envio recusado ou
	 * não concluído é substituído). O tamanho informado é só um aviso antecipado: o arquivo é
	 * conferido de verdade na confirmação.
	 *
	 * @throws NotFoundException concurso ou prova relacionada que não é do usuário
	 * @throws BadRequestException espaço ocupado, gabarito numa prova que já o traz, grande demais ou
	 * limite atingido
	 */
	@Transactional
	public UploadStarted start(String userId, UUID targetId, Kind kind, UUID paperId, String originalName,
			long sizeBytes) {
		checkTarget(userId, targetId);
		if (kind != Kind.EDITAL && paperId == null) {
			throw new BadRequestException("informe a prova relacionada (paperId)");
		}
		if (paperId != null) {
			StudyPaper paper = findPaper(userId, targetId, paperId);
			if (kind == Kind.GABARITO && paper.isAnswersIncluded()) {
				throw new BadRequestException("o gabarito desta prova está no mesmo arquivo da prova");
			}
			freeSlot(paperId, kind);
		}
		if (sizeBytes > DocumentService.MAX_BYTES) {
			throw new BadRequestException(
					"arquivo grande demais (máximo de %d MB)".formatted(DocumentService.MAX_BYTES / 1024 / 1024));
		}
		if (repository.countByStudyTargetIdAndStatusNot(targetId, StudyDocument.Status.REJECTED)
				>= MAX_DOCUMENTS_PER_TARGET) {
			throw new BadRequestException(
					"este concurso já tem %d arquivos: remova algum para enviar outro".formatted(MAX_DOCUMENTS_PER_TARGET));
		}
		if (repository.countByUserIdAndCreatedAtAfter(userId, clock.instant().minus(Duration.ofDays(1)))
				>= MAX_UPLOADS_PER_DAY) {
			throw new BadRequestException("limite de %d envios por dia atingido: tente amanhã".formatted(MAX_UPLOADS_PER_DAY));
		}
		StudyDocument document = repository.save(new StudyDocument(userId, targetId, kind, paperId,
				originalName.strip()));
		var upload = documents.uploadUrl(document.uploadId());
		return new UploadStarted(view(document), upload.url(), upload.contentType());
	}

	/**
	 * Confirma o envio: confere o arquivo e marca como pronto ou recusado (com o motivo). Confirmar de
	 * novo um arquivo já conferido devolve o mesmo resultado.
	 *
	 * @throws NotFoundException arquivo que não é do usuário
	 */
	@Transactional(noRollbackFor = BadRequestException.class)
	public DocumentView complete(String userId, UUID targetId, UUID documentId) {
		StudyDocument document = find(userId, targetId, documentId);
		if (document.getStatus() != StudyDocument.Status.UPLOADING) {
			return view(document);
		}
		try {
			var blob = documents.ingest(document.uploadId());
			document.ready(blob.sha256(), blob.sizeBytes(), blob.pages());
		}
		catch (BadRequestException e) {
			// recusado não é erro da requisição: o arquivo fica na lista com o motivo
			document.reject(e.getBody().getDetail());
			return view(document);
		}
		// a leitura do edital não é pedida aqui: a pessoa confere se enviou o arquivo certo e pede
		// (cada leitura custa LLM)
		return view(document);
	}

	/**
	 * Pede a leitura do edital do concurso-alvo (a pessoa pede depois de enviar), ou de novo, se
	 * falhou por erro passageiro. Leitura pronta ou em andamento é só devolvida (mesmo edital enviado
	 * por outra pessoa: a leitura dela, sem custo).
	 *
	 * @throws BadRequestException arquivo que não é edital do concurso ou ainda não aprovado
	 */
	@Transactional
	public DocumentView read(String userId, UUID targetId, UUID documentId) {
		StudyDocument document = find(userId, targetId, documentId);
		if (!document.isTargetEdital() || document.getStatus() != StudyDocument.Status.READY) {
			throw new BadRequestException("só o edital do concurso, já enviado, pode ser lido");
		}
		return view(document, requestReading(document));
	}

	/**
	 * Escolhe o cargo da pessoa entre os da leitura do edital. Só grava: o conteúdo programático é
	 * pedido à parte ({@link #readSyllabus}), quando a pessoa confere o cargo (cada leitura custa
	 * LLM). No concurso cadastrado à mão vira também o cargo do concurso; no da lista, o cargo do
	 * concurso continua o coletado (os nomes não batem com os do edital).
	 *
	 * @throws BadRequestException leitura não está pronta ou o cargo não está nela
	 */
	@Transactional
	public DocumentView choosePosition(String userId, UUID targetId, UUID documentId, String name,
			String specialty) {
		StudyDocument document = find(userId, targetId, documentId);
		Map<UUID, Extraction> readings = readings(document);
		Extraction reading = readings.get(document.getExtractionId());
		if (!document.isTargetEdital() || reading == null || reading.getStatus() != Extraction.Status.DONE) {
			throw new BadRequestException("o cargo é escolhido na leitura pronta do edital do concurso");
		}
		boolean found = EditalOverview.parse(reading.getResult())
			.positions()
			.stream()
			.anyMatch(p -> name.equals(p.name()) && Objects.equals(specialty, p.specialty()));
		if (!found) {
			throw new BadRequestException("cargo não está na leitura do edital: " + label(name, specialty));
		}
		document.choosePosition(name, specialty);
		StudyTarget target = targets.findByIdAndUserId(targetId, userId).orElseThrow();
		if (target.getExamId() == null) {
			target.positionName(label(name, specialty));
		}
		// outro cargo desfaz o vínculo com o conteúdo do anterior (StudyDocument.choosePosition)
		return view(document, readings);
	}

	/**
	 * Pede a leitura do conteúdo programático do cargo escolhido, ou de novo, se falhou por erro
	 * passageiro. Pronta ou em andamento é só devolvida (mesmo edital e cargo de outra pessoa: a
	 * leitura dela, sem custo).
	 *
	 * @throws BadRequestException nenhum cargo escolhido neste edital
	 */
	@Transactional
	public DocumentView readSyllabus(String userId, UUID targetId, UUID documentId) {
		StudyDocument document = find(userId, targetId, documentId);
		if (document.getPositionName() == null) {
			throw new BadRequestException("escolha o cargo antes de pedir o conteúdo programático");
		}
		Map<UUID, Extraction> views = new HashMap<>(readings(document));
		Extraction syllabus = requestSyllabus(document);
		views.put(syllabus.getId(), syllabus);
		return view(document, views);
	}

	/** Leitura do edital e do conteúdo do arquivo, numa query. */
	private Map<UUID, Extraction> readings(StudyDocument document) {
		List<UUID> ids = Stream.of(document.getExtractionId(), document.getSyllabusExtractionId())
			.filter(Objects::nonNull)
			.toList();
		return ids.isEmpty() ? Map.of() : extractions.find(ids);
	}

	/** Mesmo edital e mesmo cargo (de qualquer pessoa) = mesma leitura. */
	private Extraction requestSyllabus(StudyDocument document) {
		// HashMap: a especialidade pode ser nula (cargo sem áreas)
		Map<String, Object> position = new HashMap<>();
		position.put("name", document.getPositionName());
		position.put("specialty", document.getPositionSpecialty());
		Extraction extraction = extractions.request(ExtractionTask.EDITAL_SYLLABUS,
				List.of(document.getBlobSha256()), Map.of("position", position));
		document.syllabus(extraction.getId());
		return extraction;
	}

	/** "Analista Judiciário – Direito": o mesmo rótulo que o web mostra. */
	private static String label(String name, String specialty) {
		return specialty == null ? name : name + " – " + specialty;
	}

	/** Mesmo edital (de qualquer pessoa) = mesma leitura: o ExtractionService devolve a existente. */
	private Map<UUID, Extraction> requestReading(StudyDocument document) {
		Extraction extraction = extractions.request(ExtractionTask.EDITAL_OVERVIEW, List.of(document.getBlobSha256()),
				Map.of());
		document.reading(extraction.getId());
		return Map.of(extraction.getId(), extraction);
	}

	/** O blob continua (pode ser de outra pessoa também); só some o vínculo. */
	@Transactional
	public void remove(String userId, UUID targetId, UUID documentId) {
		repository.delete(find(userId, targetId, documentId));
	}

	/** Todo dia de madrugada: envios começados e nunca confirmados somem. */
	@Scheduled(cron = "0 30 3 * * *", zone = "America/Sao_Paulo")
	@Transactional
	public void deleteStaleUploads() {
		int deleted = repository.deleteStaleUploads(clock.instant().minus(Duration.ofDays(1)));
		if (deleted > 0) {
			log.info("{} envios abandonados apagados", deleted);
		}
	}

	/** Espaço com arquivo aprovado não é sobrescrito (remover é decisão explícita); o resto é substituído. */
	private void freeSlot(UUID paperId, Kind kind) {
		repository.findByPaperIdAndKind(paperId, kind).ifPresent(current -> {
			if (current.getStatus() == StudyDocument.Status.READY) {
				throw new BadRequestException("esta prova já tem %s: remova o arquivo atual para enviar outro"
					.formatted(switch (kind) {
						case PROVA -> "o PDF da prova";
						case GABARITO -> "o gabarito";
						case EDITAL -> "o edital";
					}));
			}
			repository.delete(current);
			// o índice único (paper_id, kind) não espera o fim da transação
			repository.flush();
		});
	}

	private void checkTarget(String userId, UUID targetId) {
		if (targets.findByIdAndUserId(targetId, userId).isEmpty()) {
			throw new NotFoundException("concurso %s não está na sua carteira".formatted(targetId));
		}
	}

	private StudyPaper findPaper(String userId, UUID targetId, UUID paperId) {
		return papers.findByIdAndStudyTargetIdAndUserId(paperId, targetId, userId)
			.orElseThrow(() -> new NotFoundException("prova %s não encontrada".formatted(paperId)));
	}

	private StudyDocument find(String userId, UUID targetId, UUID documentId) {
		return repository.findByIdAndStudyTargetIdAndUserId(documentId, targetId, userId)
			.orElseThrow(() -> new NotFoundException("arquivo %s não encontrado".formatted(documentId)));
	}

	private PaperView paperView(StudyPaper p, Map<Kind, StudyDocument> slots) {
		return new PaperView(p.getId(), p.getYear(), p.getVariant(), p.isAnswersIncluded(), p.getCreatedAt(),
				viewOrNull(slots.get(Kind.PROVA)), viewOrNull(slots.get(Kind.GABARITO)), viewOrNull(slots.get(Kind.EDITAL)));
	}

	private DocumentView viewOrNull(StudyDocument d) {
		return d == null ? null : view(d);
	}

	private DocumentView view(StudyDocument d) {
		return view(d, Map.of());
	}

	/** @param readings leituras já buscadas, por id (a do arquivo pode não estar: fica sem leitura) */
	private DocumentView view(StudyDocument d, Map<UUID, Extraction> readings) {
		URI url = d.getStatus() == StudyDocument.Status.READY ? documents.viewUrl(d.getBlobSha256()) : null;
		Extraction extraction = d.getExtractionId() == null ? null : readings.get(d.getExtractionId());
		Extraction syllabus = d.getSyllabusExtractionId() == null ? null : readings.get(d.getSyllabusExtractionId());
		return new DocumentView(d.getId(), d.getKind(), d.getOriginalName(), d.getStatus(), d.getRejectionReason(),
				d.getSizeBytes(), d.getPages(), d.getCreatedAt(), url,
				extraction == null ? null : Reading.of(extraction),
				d.getPositionName() == null ? null : new Position(d.getPositionName(), d.getPositionSpecialty()),
				syllabus == null ? null : SyllabusReading.of(syllabus));
	}

	private static String blankToNull(String text) {
		return text == null || text.isBlank() ? null : text.strip();
	}

}
