package com.gabarit.core.user;

import com.gabarit.core.exam.CatalogPosition;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Preferências de estudo do usuário logado. O usuário vem do token, nunca do corpo da requisição. */
@RestController
class ProfileController {

	record PositionResponse(long id, String slug, String name) {

		static PositionResponse of(CatalogPosition position) {
			return new PositionResponse(position.getId(), position.getSlug(), position.getName());
		}

	}

	record ProfileResponse(List<PositionResponse> positions, List<String> ufs) {

		static ProfileResponse of(UserProfileService.ProfileView view) {
			return new ProfileResponse(view.positions().stream().map(PositionResponse::of).toList(), view.ufs());
		}

	}

	/** Estado completo das preferências (substitui o que existia). */
	record UpdateProfileRequest(@NotNull List<@NotNull Long> positionIds, @NotNull List<@NotBlank String> ufs) {
	}

	private final UserProfileService profileService;

	ProfileController(UserProfileService profileService) {
		this.profileService = profileService;
	}

	@GetMapping("/me/profile")
	ProfileResponse get(@AuthenticationPrincipal Jwt jwt) {
		return ProfileResponse.of(profileService.get(jwt.getSubject()));
	}

	@PutMapping("/me/profile")
	ProfileResponse update(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateProfileRequest request) {
		return ProfileResponse.of(profileService.update(jwt.getSubject(), request.positionIds(), request.ufs()));
	}

}
