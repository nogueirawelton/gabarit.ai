package com.gabarit.core.notification;

import com.gabarit.core.notification.NotificationService.NotificationView;
import com.gabarit.core.shared.PageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Avisos do usuário logado (sininho do app). */
@RestController
class NotificationController {

	record UnreadCount(long count) {
	}

	private final NotificationService notifications;

	NotificationController(NotificationService notifications) {
		this.notifications = notifications;
	}

	@GetMapping("/me/notifications")
	PageResponse<NotificationView> list(@AuthenticationPrincipal Jwt jwt,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
		return PageResponse.of(notifications.list(jwt.getSubject(), page, size));
	}

	/** O web consulta isto de tempos em tempos (contador do sininho): uma query só. */
	@GetMapping("/me/notifications/unread-count")
	UnreadCount unreadCount(@AuthenticationPrincipal Jwt jwt) {
		return new UnreadCount(notifications.unreadCount(jwt.getSubject()));
	}

	@PostMapping("/me/notifications/{id}/read")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void markRead(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
		notifications.markRead(jwt.getSubject(), id);
	}

	@PostMapping("/me/notifications/read-all")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void markAllRead(@AuthenticationPrincipal Jwt jwt) {
		notifications.markAllRead(jwt.getSubject());
	}

}
