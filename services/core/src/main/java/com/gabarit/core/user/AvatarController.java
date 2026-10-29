package com.gabarit.core.user;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;

/**
 * Foto de perfil do usuário logado. Upload multipart (campo {@code file}), limitado pelo
 * spring.servlet.multipart (acima disso: 413). Leitura: {@code avatarUrl} do GET /me/profile.
 */
@RestController
class AvatarController {

	record AvatarResponse(URI url) {
	}

	private final AvatarService avatars;

	AvatarController(AvatarService avatars) {
		this.avatars = avatars;
	}

	@PostMapping(path = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	AvatarResponse upload(@AuthenticationPrincipal Jwt jwt, @RequestPart("file") MultipartFile file)
			throws IOException {
		return new AvatarResponse(avatars.upload(jwt.getSubject(), file.getBytes()));
	}

	@DeleteMapping("/me/avatar")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void remove(@AuthenticationPrincipal Jwt jwt) {
		avatars.remove(jwt.getSubject());
	}

}
