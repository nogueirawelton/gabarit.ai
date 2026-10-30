package com.gabarit.core.simulation;

import com.gabarit.core.extraction.Extraction;
import com.gabarit.core.extraction.ExtractionService;
import com.gabarit.core.extraction.ExtractionTask;
import com.gabarit.core.shared.NotFoundException;
import com.gabarit.core.study.EditalSyllabus;
import com.gabarit.core.study.StudyDocumentService;
import com.gabarit.core.study.StudyDocumentService.TargetSyllabus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** Resumo de tópico (sem banco: a leitura é do pacote extraction). */
class SummaryServiceTest {

	static final String DONE = """
			{"summary": "Ato administrativo é...", "key_points": ["Atributos"], "pitfalls": ["Confundir anulação"],
			 "laws": [{"name": "Lei nº 9.784/1999", "kind": "LAW", "number": "9.784", "year": 1999, "articles": ["art. 53"]},
			          {"name": "Constituição Federal", "kind": "CONSTITUTION", "number": null, "year": 1988, "articles": []}],
			 "readings": [{"author": "Maria Sylvia Zanella Di Pietro", "title": "Direito Administrativo"}]}
			""";

	StudyDocumentService documents = mock(StudyDocumentService.class);

	ExtractionService extractions = mock(ExtractionService.class);

	SummaryService service = new SummaryService(documents, extractions);

	UUID targetId = UUID.randomUUID();

	@BeforeEach
	void conteudo() {
		given(documents.targetSyllabus("maria", targetId)).willReturn(Optional.of(new TargetSyllabus(UUID.randomUUID(),
				new EditalSyllabus(true, List.of(new EditalSyllabus.Subject("Direito Administrativo", null,
						List.of("Atos administrativos."), 1))))));
	}

	@Test
	void semPedidoNaoHaResumo() {
		assertThat(service.get("maria", targetId, 0, 0)).isEmpty();
		verify(extractions).lookup(ExtractionTask.TOPIC_SUMMARY, List.of(),
				Map.of("subject", "Direito Administrativo", "topic", "Atos administrativos."));
		assertThatThrownBy(() -> service.get("maria", targetId, 0, 3)).isInstanceOf(NotFoundException.class);
	}

	@Test
	void resumoProntoComOsLinksDasLeisMontadosAqui() {
		Extraction done = mock(Extraction.class);
		given(done.getStatus()).willReturn(Extraction.Status.DONE);
		given(done.getResult()).willReturn(DONE);
		given(extractions.request(any(), any(), anyList(), anyMap())).willReturn(done);

		var summary = service.request("maria", targetId, 0, 0);

		verify(extractions).request("maria", ExtractionTask.TOPIC_SUMMARY, List.of(),
				Map.of("subject", "Direito Administrativo", "topic", "Atos administrativos."));
		assertThat(summary.keyPoints()).containsExactly("Atributos");
		assertThat(summary.laws()).extracting(SummaryService.LawView::url)
			.containsExactly("https://www.lexml.gov.br/busca/search?keyword=lei+9784+1999",
					"https://www.planalto.gov.br/ccivil_03/constituicao/constituicao.htm");
		assertThat(summary.readings()).singleElement()
			.satisfies(r -> assertThat(r.author()).isEqualTo("Maria Sylvia Zanella Di Pietro"));
	}

}
