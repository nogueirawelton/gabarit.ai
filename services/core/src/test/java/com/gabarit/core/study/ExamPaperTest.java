package com.gabarit.core.study;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Lê a saída do worker (EXAM_PAPER v1) e resume para o web. */
class ExamPaperTest {

	static final String RESULT = """
			{"variant": "Tipo 1", "answer_key_found": true,
			 "texts": [{"id": 1, "content": "Texto I..."}],
			 "questions": [
			  {"number": 1, "subject": "Língua Portuguesa", "kind": "MULTIPLE_CHOICE", "text_id": 1,
			   "statement": "De acordo com o texto...", "options": [{"letter": "A", "text": "..."},
			   {"letter": "B", "text": "..."}], "answer": "B", "annulled": false, "has_image": false, "page": 2},
			  {"number": 2, "subject": "Língua Portuguesa", "kind": "MULTIPLE_CHOICE", "text_id": null,
			   "statement": "...", "options": [], "answer": null, "annulled": true, "has_image": false, "page": 2},
			  {"number": 3, "subject": "Direito Administrativo", "kind": "TRUE_FALSE", "text_id": null,
			   "statement": "...", "options": [], "answer": null, "annulled": false, "has_image": true, "page": 3},
			  {"number": 4, "subject": null, "kind": "ESSAY", "text_id": null,
			   "statement": "Redija...", "options": [], "answer": null, "annulled": false, "has_image": false, "page": 4}]}
			""";

	@Test
	void leAsQuestoes() {
		ExamPaper paper = ExamPaper.parse(RESULT);

		assertThat(paper.texts()).singleElement().satisfies(t -> assertThat(t.id()).isEqualTo(1));
		assertThat(paper.questions().getFirst()).satisfies(q -> {
			assertThat(q.textId()).isEqualTo(1);
			assertThat(q.options()).extracting(ExamPaper.Option::letter).containsExactly("A", "B");
			assertThat(q.answer()).isEqualTo("B");
		});
		assertThat(paper.questions().get(2).hasImage()).isTrue();
	}

	@Test
	void resumoContaAnuladasESemRespostaPorDisciplina() {
		ExamPaper.Summary summary = ExamPaper.parse(RESULT).summary();

		assertThat(summary.questions()).isEqualTo(4);
		assertThat(summary.annulled()).isEqualTo(1);
		// a 3 não tem resposta; a anulada e a discursiva não contam
		assertThat(summary.withoutAnswer()).isEqualTo(1);
		assertThat(summary.bySubject()).containsExactly(new ExamPaper.SubjectCount("Língua Portuguesa", 2),
				new ExamPaper.SubjectCount("Direito Administrativo", 1), new ExamPaper.SubjectCount("Sem disciplina", 1));
	}

}
