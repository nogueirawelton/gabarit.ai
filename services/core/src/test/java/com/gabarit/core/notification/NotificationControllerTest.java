package com.gabarit.core.notification;

import com.gabarit.core.exam.ExamStatus;
import com.gabarit.core.exam.ExamSummary;
import com.gabarit.core.shared.NotFoundException;
import com.gabarit.core.shared.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
@Import(SecurityConfig.class)
class NotificationControllerTest {

	static final UUID ID = UUID.fromString("0193a1f0-0000-7000-8000-000000000003");

	@Autowired
	MockMvc mvc;

	@MockitoBean
	NotificationService service;

	@Test
	void semTokenE401() throws Exception {
		mvc.perform(get("/me/notifications/unread-count")).andExpect(status().isUnauthorized());
	}

	@Test
	void listaOsAvisosDoUsuarioDoToken() throws Exception {
		var exam = new ExamSummary(UUID.randomUUID(), "Prefeitura de Santos - SP", "SP", null, null, null, null, null, null,
				ExamStatus.OPEN, "https://example.com/7", List.of());
		given(service.list("kc-maria", 0, 20)).willReturn(new PageImpl<>(
				List.of(new NotificationService.NotificationView(ID, Instant.parse("2026-11-10T11:00:00Z"), false,
						Notification.Type.NEW_EXAM, exam, null)),
				PageRequest.of(0, 20), 1));

		mvc.perform(get("/me/notifications").with(jwt().jwt(t -> t.subject("kc-maria"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].id").value(ID.toString()))
			.andExpect(jsonPath("$.items[0].read").value(false))
			.andExpect(jsonPath("$.items[0].type").value("NEW_EXAM"))
			.andExpect(jsonPath("$.items[0].exam.title").value("Prefeitura de Santos - SP"))
			.andExpect(jsonPath("$.totalElements").value(1));
	}

	@Test
	void contadorDeNaoLidos() throws Exception {
		given(service.unreadCount("kc-maria")).willReturn(4L);

		mvc.perform(get("/me/notifications/unread-count").with(jwt().jwt(t -> t.subject("kc-maria"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.count").value(4));
	}

	@Test
	void marcarComoLido() throws Exception {
		mvc.perform(post("/me/notifications/" + ID + "/read").with(jwt().jwt(t -> t.subject("kc-maria"))))
			.andExpect(status().isNoContent());
		mvc.perform(post("/me/notifications/read-all").with(jwt().jwt(t -> t.subject("kc-maria"))))
			.andExpect(status().isNoContent());

		verify(service).markRead("kc-maria", ID);
		verify(service).markAllRead("kc-maria");
	}

	@Test
	void avisoDeOutroUsuarioE404() throws Exception {
		UUID other = UUID.randomUUID();
		willThrow(new NotFoundException("aviso não encontrado")).given(service).markRead("kc-maria", other);

		mvc.perform(post("/me/notifications/" + other + "/read").with(jwt().jwt(t -> t.subject("kc-maria"))))
			.andExpect(status().isNotFound());
	}

}
