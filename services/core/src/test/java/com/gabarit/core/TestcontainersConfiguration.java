package com.gabarit.core;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.io.IOException;

// Aplicação inteira: Postgres + RabbitMQ + MinIO. Mesmas imagens do docker-compose.yml.
@TestConfiguration(proxyBeanMethods = false)
@Import({ PostgresTestConfiguration.class, MinioTestConfiguration.class })
public class TestcontainersConfiguration {

	private static final String DEFINITIONS = "/tmp/definitions.json";

	@Bean
	@ServiceConnection
	RabbitMQContainer rabbitContainer() throws IOException, InterruptedException {
		RabbitMQContainer rabbit = new RabbitMQContainer(
				DockerImageName.parse("rabbitmq:3.13.7-management").asCompatibleSubstituteFor("rabbitmq"))
			.withCopyFileToContainer(
					MountableFile.forHostPath(RepoFiles.path("infra/rabbitmq/definitions.json")), DEFINITIONS);
		rabbit.start();
		importTopology(rabbit);
		return rabbit;
	}

	// mesma topologia do ambiente (o que o rabbitmq-init faz no compose), antes dos listeners subirem
	private static void importTopology(RabbitMQContainer rabbit) throws IOException, InterruptedException {
		ExecResult result = rabbit.execInContainer("rabbitmqadmin", "-u", rabbit.getAdminUsername(), "-p",
				rabbit.getAdminPassword(), "import", DEFINITIONS);
		if (result.getExitCode() != 0) {
			throw new IllegalStateException("falha ao importar a topologia: " + result.getStderr());
		}
	}

}
