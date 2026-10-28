package com.gabarit.core.user;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Quem está logado, segundo o token. O id é o "sub" do Keycloak: estável, use-o como chave do usuário. */
@RestController
class MeController {

	record Me(String id, String email, String name, String givenName) {
	}

	@GetMapping("/me")
	Me me(@AuthenticationPrincipal Jwt jwt) {
		return new Me(jwt.getSubject(), jwt.getClaimAsString("email"), jwt.getClaimAsString("name"),
				jwt.getClaimAsString("given_name"));
	}

}
