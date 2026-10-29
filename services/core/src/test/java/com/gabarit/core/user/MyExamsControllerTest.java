package com.gabarit.core.user;

import com.gabarit.core.exam.CatalogPosition;
import com.gabarit.core.exam.EducationLevel;
import com.gabarit.core.exam.ExamFilter;
import com.gabarit.core.exam.ExamService;
import com.gabarit.core.exam.ExamStatus;
import com.gabarit.core.shared.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MyExamsController.class)
@Import(SecurityConfig.class)
class MyExamsControllerTest {

	@Autowired
	MockMvc mvc;

	@MockitoBean
	UserProfileService profiles;

	@MockitoBean
	ExamService exams;

	static CatalogPosition position(String source, String slug) {
		CatalogPosition position = mock(CatalogPosition.class);
		given(position.getSource()).willReturn(source);
		given(position.getSlug()).willReturn(slug);
		return position;
	}

	@Test
	void perfilSemCargoDevolveListaVaziaSemBuscar() throws Exception {
		given(profiles.get("kc-maria")).willReturn(new UserProfileService.ProfileView(List.of(), List.of("SP"), List.of(), null, true));

		mvc.perform(get("/me/exams").with(jwt().jwt(t -> t.subject("kc-maria"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items").isEmpty())
			.andExpect(jsonPath("$.totalElements").value(0));

		verifyNoInteractions(exams);
	}

	@Test
	void buscaPelosCargosEUfsDoPerfilDoToken() throws Exception {
		var enfermeiro = position("pciconcursos", "enfermeiro");
		var analista = position("pciconcursos", "analista");
		given(profiles.get("kc-maria"))
			.willReturn(new UserProfileService.ProfileView(List.of(enfermeiro, analista), List.of("SP", "MG"),
					List.of(EducationLevel.MEDIO), null, true));
		given(exams.search(any(), anyInt(), anyInt())).willReturn(Page.empty());

		mvc.perform(get("/me/exams").with(jwt().jwt(t -> t.subject("kc-maria"))).param("status", "OPEN", "UPCOMING"))
			.andExpect(status().isOk());

		verify(exams).search(new ExamFilter(Set.of("SP", "MG"), Set.of(ExamStatus.OPEN, ExamStatus.UPCOMING),
				Set.of(EducationLevel.MEDIO), null,
				Map.of("pciconcursos", Set.of("enfermeiro", "analista"))), 0, 20);
	}

}
