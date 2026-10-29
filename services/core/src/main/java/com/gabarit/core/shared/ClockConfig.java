package com.gabarit.core.shared;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Relógio da aplicação no fuso de Brasília: "inscrições até 30/10" é o dia 30 aqui, não em UTC
 * (com LocalDate.now() em UTC, o concurso "fecharia" às 21h). Injetado para os testes fixarem a data.
 */
@Configuration(proxyBeanMethods = false)
public class ClockConfig {

	public static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

	@Bean
	Clock clock() {
		return Clock.system(ZONE);
	}

}
