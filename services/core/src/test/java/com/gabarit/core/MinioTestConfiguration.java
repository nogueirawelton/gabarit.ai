package com.gabarit.core;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.io.IOException;

// MinIO com o mesmo init da infra (bucket, policy e usuário do core): o core acessa com o usuário
// restrito, como no ambiente. Mesma imagem do infra/minio/Dockerfile.
@TestConfiguration(proxyBeanMethods = false)
public class MinioTestConfiguration {

	static final String CORE_PASSWORD = "core-test-password";

	@Bean
	MinIOContainer minioContainer() throws IOException, InterruptedException {
		MinIOContainer minio = new MinIOContainer(DockerImageName.parse("coollabsio/minio:RELEASE.2025-10-15T17-29-55Z")
			.asCompatibleSubstituteFor("minio/minio"))
			.withCopyFileToContainer(MountableFile.forHostPath(RepoFiles.path("infra/minio/init.sh"), 0755), "/init.sh")
			.withCopyFileToContainer(MountableFile.forHostPath(RepoFiles.path("infra/minio/policies/core.json")),
					"/policies/core.json")
			.withCopyFileToContainer(MountableFile.forHostPath(RepoFiles.path("infra/minio/policies/extraction.json")),
					"/policies/extraction.json");
		minio.start();
		runInit(minio);
		return minio;
	}

	@Bean
	DynamicPropertyRegistrar storageProperties(MinIOContainer minio) {
		return registry -> {
			registry.add("gabarit.storage.url", minio::getS3URL);
			// fora do compose não há rede interna: o teste lê as URLs pré-assinadas pelo mesmo endereço
			registry.add("gabarit.storage.public-url", minio::getS3URL);
			registry.add("gabarit.storage.access-key", () -> "core");
			registry.add("gabarit.storage.secret-key", () -> CORE_PASSWORD);
		};
	}

	// o que o minio-init faz no compose, antes do core subir
	private static void runInit(MinIOContainer minio) throws IOException, InterruptedException {
		ExecResult result = minio.execInContainer("env", "MINIO_URL=http://localhost:9000",
				"MINIO_ROOT_USER=" + minio.getUserName(), "MINIO_ROOT_PASSWORD=" + minio.getPassword(),
				"CORE_MINIO_PASSWORD=" + CORE_PASSWORD, "EXTRACTION_MINIO_PASSWORD=extraction-test-password",
				"MC_CONFIG_DIR=/tmp/.mc", "/init.sh");
		if (result.getExitCode() != 0) {
			throw new IllegalStateException("falha no init do MinIO: " + result.getStdout() + result.getStderr());
		}
	}

}
