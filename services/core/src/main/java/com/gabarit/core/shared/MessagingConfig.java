package com.gabarit.core.shared;

import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/**
 * Mensagens em JSON (Jackson 3, o mesmo JsonMapper do Spring MVC).
 *
 * <p>Filas, exchanges e bindings NÃO são declarados aqui: são da infra
 * (infra/rabbitmq/definitions.json). Declarar com argumento diferente derruba o listener.
 */
@Configuration(proxyBeanMethods = false)
class MessagingConfig {

	// usado pelos @RabbitListener e pelo RabbitTemplate; o tipo vem do parâmetro do listener
	@Bean
	JacksonJsonMessageConverter jacksonJsonMessageConverter(JsonMapper jsonMapper) {
		return new JacksonJsonMessageConverter(jsonMapper);
	}

}
