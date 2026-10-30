package com.gabarit.core.study;

import com.gabarit.core.extraction.Extraction;
import com.gabarit.core.extraction.ExtractionFinishedEvent;
import com.gabarit.core.extraction.ExtractionTask;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Leitura de edital terminou → um {@link EditalReadEvent} por concurso da carteira que tem esse
 * edital (a leitura é por conteúdo: o mesmo PDF de várias pessoas é lido uma vez e avisa todas).
 *
 * <p>Sem aviso: edital confirmado quando a leitura já tinha terminado (o mesmo PDF já lido para
 * outra pessoa: aparece pronta na hora), e o caso raro de a leitura terminar antes de o envio do
 * edital ser gravado (a leitura leva segundos a minutos; o envio, milissegundos).
 */
@Component
class EditalReadingListener {

	private final StudyDocumentRepository documents;

	private final StudyPaperRepository papers;

	private final StudyTargetRepository targets;

	private final ApplicationEventPublisher events;

	EditalReadingListener(StudyDocumentRepository documents, StudyPaperRepository papers,
			StudyTargetRepository targets, ApplicationEventPublisher events) {
		this.documents = documents;
		this.papers = papers;
		this.targets = targets;
		this.events = events;
	}

	@EventListener
	@Transactional
	public void onExtractionFinished(ExtractionFinishedEvent event) {
		boolean ok = event.status() == Extraction.Status.DONE;
		switch (event.task()) {
			case EDITAL_OVERVIEW -> editalRead(event, ok);
			case EDITAL_SYLLABUS -> notify(documents.findBySyllabusExtractionId(event.extractionId())
				.stream()
				.map(StudyDocument::getStudyTargetId)
				.toList(), StudyReadyEvent.What.SYLLABUS, ok);
			case EXAM_PAPER -> notify(papers.findByExtractionId(event.extractionId())
				.stream()
				.map(StudyPaper::getStudyTargetId)
				.toList(), StudyReadyEvent.What.PAPER, ok);
			default -> {
				// as outras tarefas avisam pelo próprio pacote (ou não avisam)
			}
		}
	}

	/** Um aviso por concurso (o mesmo conteúdo em vários concursos: a leitura é uma só e avisa todos). */
	private void notify(List<UUID> targetIds, StudyReadyEvent.What what, boolean ok) {
		targets.findAllById(targetIds.stream().distinct().toList())
			.forEach(target -> events
				.publishEvent(new StudyReadyEvent(target.getUserId(), target.getId(), target.getTitle(), what, ok)));
	}

	private void editalRead(ExtractionFinishedEvent event, boolean ok) {
		var withReading = documents.findByExtractionId(event.extractionId());
		Map<UUID, StudyTarget> byId = targets
			.findAllById(withReading.stream().map(StudyDocument::getStudyTargetId).distinct().toList())
			.stream()
			.collect(Collectors.toMap(StudyTarget::getId, Function.identity()));
		withReading.stream()
			.map(StudyDocument::getStudyTargetId)
			.distinct()
			.map(byId::get)
			.forEach(target -> events
				.publishEvent(new EditalReadEvent(target.getUserId(), target.getId(), target.getTitle(), ok)));
	}

}
