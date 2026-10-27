package com.gabarit.core;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Acha arquivos da raiz do repo (contracts/, infra/) subindo a partir do diretório atual.
 * No host/CI: services/core → ../../. No container de dev: montados em /contracts e /infra
 * (docker-compose.override.yml), que é o que sobra subindo de /app.
 */
public final class RepoFiles {

	private RepoFiles() {
	}

	public static Path path(String relative) {
		for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
			Path candidate = dir.resolve(relative);
			if (Files.isRegularFile(candidate)) {
				return candidate;
			}
		}
		throw new IllegalStateException(relative + " não encontrado subindo a partir de " + Path.of("").toAbsolutePath());
	}

}
