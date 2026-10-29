package com.gabarit.core.study;

import com.gabarit.core.document.DocumentService;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

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

	/** Arquivo. {@code url}: para abrir o PDF (só aprovados, expira em 1h). */
	public record DocumentView(UUID id, Kind kind, String originalName, StudyDocument.Status status,
			String rejectionReason, Long sizeBytes, Integer pages, Instant createdAt, URI url) {
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

	private final Clock clock;

	StudyDocumentService(StudyDocumentRepository repository, StudyPaperRepository papers,
			StudyTargetRepository targets, DocumentService documents, Clock clock) {
		this.repository = repository;
		this.papers = papers;
		this.targets = targets;
		this.documents = documents;
		this.clock = clock;
	}

	/** Editais e provas relacionadas, na ordem em que foram adicionados. Número fixo de queries. */
	@Transactional(readOnly = true)
	public Materials materials(String userId, UUID targetId) {
		checkTarget(userId, targetId);
		List<StudyDocument> all = repository.findByStudyTargetIdOrderByCreatedAtAscIdAsc(targetId);
		Map<UUID, Map<Kind, StudyDocument>> byPaper = all.stream()
			.filter(d -> d.getPaperId() != null)
			.collect(Collectors.groupingBy(StudyDocument::getPaperId,
					Collectors.toMap(StudyDocument::getKind, Function.identity())));
		List<DocumentView> editais = all.stream()
			.filter(d -> d.getKind() == Kind.EDITAL && d.getPaperId() == null)
			.map(this::view)
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
		}
		return view(document);
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
		URI url = d.getStatus() == StudyDocument.Status.READY ? documents.viewUrl(d.getBlobSha256()) : null;
		return new DocumentView(d.getId(), d.getKind(), d.getOriginalName(), d.getStatus(), d.getRejectionReason(),
				d.getSizeBytes(), d.getPages(), d.getCreatedAt(), url);
	}

	private static String blankToNull(String text) {
		return text == null || text.isBlank() ? null : text.strip();
	}

}
