package com.gabarit.core.shared;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Duration;
import java.util.List;

/**
 * API protegida por token do Keycloak (Bearer JWT). Issuer, audience e chaves vêm do
 * application.yml (spring.security.oauth2.resourceserver.jwt).
 *
 * <p>Sem sessão e sem CSRF: o token vai no header Authorization, não em cookie.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

	@Bean
	SecurityFilterChain api(HttpSecurity http) throws Exception {
		return http
			.authorizeHttpRequests(auth -> auth
				// healthcheck do container
				.requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
				// preflight do CORS não leva token
				.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
				.anyRequest().authenticated())
			.oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.csrf(AbstractHttpConfigurer::disable)
			.cors(Customizer.withDefaults())
			.build();
	}

	// o web roda em outra origem (dev: localhost:5173 → localhost:8080)
	@Bean
	CorsConfigurationSource corsConfigurationSource(@Value("${gabarit.web-url}") String webUrl) {
		CorsConfiguration cors = new CorsConfiguration();
		cors.setAllowedOrigins(List.of(webUrl));
		cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE"));
		cors.setAllowedHeaders(List.of("Authorization", "Content-Type"));
		cors.setMaxAge(Duration.ofHours(1));
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", cors);
		return source;
	}

}
