package com.gabarit.core.simulation;

import com.gabarit.core.study.ExamPaper;
import com.gabarit.core.study.StudyDocumentService.PaperQuestions;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Questões de uma prova lida, copiadas do resultado da leitura na primeira vez que alguém precisa
 * delas (simulado, ligação a tópicos). Compartilhadas: a mesma leitura, as mesmas questões.
 */
@Component
class QuestionCatalog {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private final QuestionRepository questions;

	private final TransactionTemplate copy;

	QuestionCatalog(QuestionRepository questions, TransactionTemplate transaction) {
		this.questions = questions;
		// cópia em transação própria: duas pessoas copiando a mesma prova ao mesmo tempo, a segunda
		// bate na unique e usa a cópia da primeira
		this.copy = new TransactionTemplate(transaction.getTransactionManager());
		this.copy.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}

	/** Todas as questões da leitura (discursivas também), na ordem da prova. */
	List<Question> questions(PaperQuestions source) {
		if (!questions.existsByExtractionId(source.extractionId())) {
			copy(source);
		}
		return questions.findByExtractionIdOrderByOrdinal(source.extractionId());
	}

	private void copy(PaperQuestions source) {
		Map<Integer, String> texts = source.paper()
			.texts()
			.stream()
			.collect(Collectors.toMap(ExamPaper.SharedText::id, ExamPaper.SharedText::content, (a, b) -> a));
		List<ExamPaper.Question> read = source.paper().questions();
		List<Question> rows = IntStream.range(0, read.size()).mapToObj(i -> {
			ExamPaper.Question q = read.get(i);
			return new Question(source.extractionId(), i, q.number(), q.subject(), Question.Kind.valueOf(q.kind()),
					q.textId() == null ? null : texts.get(q.textId()), q.statement(),
					JSON.writeValueAsString(q.options() == null ? List.of() : q.options()), q.answer(), q.annulled(),
					q.hasImage(), q.page());
		}).toList();
		try {
			copy.executeWithoutResult(status -> questions.saveAllAndFlush(rows));
		}
		catch (DataIntegrityViolationException e) {
			// outra pessoa copiou primeiro: usa a cópia dela
		}
	}

}
