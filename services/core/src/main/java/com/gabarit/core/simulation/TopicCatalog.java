package com.gabarit.core.simulation;

import com.gabarit.core.study.EditalSyllabus;
import com.gabarit.core.study.StudyDocumentService.TargetSyllabus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Disciplinas e tópicos do conteúdo de um cargo, copiados do resultado da leitura na primeira vez
 * que alguém liga questões a ele. Compartilhados, como as questões.
 */
@Component
class TopicCatalog {

	private final SyllabusSubjectRepository subjects;

	private final SyllabusTopicRepository topics;

	private final TransactionTemplate copy;

	TopicCatalog(SyllabusSubjectRepository subjects, SyllabusTopicRepository topics, TransactionTemplate transaction) {
		this.subjects = subjects;
		this.topics = topics;
		// em transação própria, pelo mesmo motivo das questões (QuestionCatalog)
		this.copy = new TransactionTemplate(transaction.getTransactionManager());
		this.copy.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}

	/** Todos os tópicos, na ordem do conteúdo (com a disciplina carregada). */
	List<SyllabusTopic> topics(TargetSyllabus syllabus) {
		if (!subjects.existsByExtractionId(syllabus.extractionId())) {
			copy(syllabus);
		}
		return topics.findBySyllabus(syllabus.extractionId());
	}

	/** Nome da disciplina do tópico. */
	String subjectOf(UUID topicId) {
		return topics.findById(topicId).map(t -> t.getSubject().getName()).orElse(null);
	}

	private void copy(TargetSyllabus syllabus) {
		List<EditalSyllabus.Subject> read = syllabus.syllabus().subjects();
		try {
			copy.executeWithoutResult(status -> {
				List<SyllabusTopic> rows = new ArrayList<>();
				for (int s = 0; s < read.size(); s++) {
					EditalSyllabus.Subject subject = read.get(s);
					SyllabusSubject saved = subjects
						.save(new SyllabusSubject(syllabus.extractionId(), s, subject.name(), subject.group()));
					List<String> texts = subject.topics() == null ? List.of() : subject.topics();
					for (int t = 0; t < texts.size(); t++) {
						rows.add(new SyllabusTopic(saved, t, texts.get(t)));
					}
				}
				topics.saveAllAndFlush(rows);
			});
		}
		catch (DataIntegrityViolationException e) {
			// outra pessoa copiou primeiro: usa a cópia dela
		}
	}

}
