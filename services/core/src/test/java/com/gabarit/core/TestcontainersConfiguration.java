package com.gabarit.core;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

// Aplicação inteira: Postgres + RabbitMQ. Mesmas imagens do docker-compose.yml.
@TestConfiguration(proxyBeanMethods = false)
@Import(PostgresTestConfiguration.class)
public class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	RabbitMQContainer rabbitContainer() {
		return new RabbitMQContainer(
				DockerImageName.parse("rabbitmq:3.13.7-management").asCompatibleSubstituteFor("rabbitmq"));
	}

}
