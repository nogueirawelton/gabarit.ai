package com.gabarit.core.user;

import com.gabarit.core.exam.CatalogPosition;
import com.gabarit.core.exam.EducationLevel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/** Preferências de estudo do usuário logado. O usuário vem do token, nunca do corpo da requisição. */
@RestController
class ProfileController {

	record PositionResponse(UUID id, String slug, String name) {

		static PositionResponse of(CatalogPosition position) {
			return new PositionResponse(position.getId(), position.getSlug(), position.getName());
		}

	}

	/** {@code avatarUrl}: URL temporária da foto (o navegador lê direto do MinIO); nulo = sem foto. */
	record ProfileResponse(List<PositionResponse> positions, List<String> ufs, List<EducationLevel> educationLevels,
			URI avatarUrl, boolean emailDigest) {
	}

	/** Estado completo das preferências (substitui o que existia). Nível desconhecido → 400. */
	record UpdateProfileRequest(@NotNull List<@NotNull UUID> positionIds, @NotNull List<@NotBlank String> ufs,
			@NotNull List<@NotNull EducationLevel> educationLevels) {
	}

	/** Resumo diário de concursos novos por e-mail. */
	record EmailDigestRequest(@NotNull Boolean emailDigest) {
	}

	private final UserProfileService profileService;

	private final AvatarService avatars;

	ProfileController(UserProfileService profileService, AvatarService avatars) {
		this.profileService = profileService;
		this.avatars = avatars;
	}

	/** Também guarda o e-mail e o nome do token: é o que o resumo diário usa (o app chama isto ao abrir). */
	@GetMapping("/me/profile")
	ProfileResponse get(@AuthenticationPrincipal Jwt jwt) {
		rememberContact(jwt);
		return response(profileService.get(jwt.getSubject()));
	}

	@PutMapping("/me/profile")
	ProfileResponse update(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateProfileRequest request) {
		var view = profileService.update(jwt.getSubject(), request.positionIds(), request.ufs(),
				request.educationLevels());
		rememberContact(jwt);
		return response(view);
	}

	@PutMapping("/me/profile/email-digest")
	ProfileResponse changeEmailDigest(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody EmailDigestRequest request) {
		var view = profileService.changeEmailDigest(jwt.getSubject(), request.emailDigest());
		rememberContact(jwt);
		return response(view);
	}

	private void rememberContact(Jwt jwt) {
		profileService.rememberContact(jwt.getSubject(), jwt.getClaimAsString("email"),
				Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified")), jwt.getClaimAsString("given_name"));
	}

	private ProfileResponse response(UserProfileService.ProfileView view) {
		return new ProfileResponse(view.positions().stream().map(PositionResponse::of).toList(), view.ufs(),
				view.educationLevels(), avatars.url(view.avatarKey()), view.emailDigest());
	}

}
