package com.gabarit.core.study;

import com.gabarit.core.extraction.Extraction;
import com.gabarit.core.extraction.ExtractionFinishedEvent;
import com.gabarit.core.extraction.ExtractionTask;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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

	private final StudyTargetRepository targets;

	private final ApplicationEventPublisher events;

	EditalReadingListener(StudyDocumentRepository documents, StudyTargetRepository targets,
			ApplicationEventPublisher events) {
		this.documents = documents;
		this.targets = targets;
		this.events = events;
	}

	@EventListener
	@Transactional
	public void onExtractionFinished(ExtractionFinishedEvent event) {
		if (event.task() != ExtractionTask.EDITAL_OVERVIEW) {
			return;
		}
		var withReading = documents.findByExtractionId(event.extractionId());
		Map<UUID, StudyTarget> byId = targets
			.findAllById(withReading.stream().map(StudyDocument::getStudyTargetId).distinct().toList())
			.stream()
			.collect(Collectors.toMap(StudyTarget::getId, Function.identity()));
		boolean ok = event.status() == Extraction.Status.DONE;
		withReading.stream()
			.map(StudyDocument::getStudyTargetId)
			.distinct()
			.map(byId::get)
			.forEach(target -> events
				.publishEvent(new EditalReadEvent(target.getUserId(), target.getId(), target.getTitle(), ok)));
	}

}
