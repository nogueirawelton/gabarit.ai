package com.gabarit.core.user;

import com.gabarit.core.shared.BadRequestException;
import com.gabarit.core.shared.ObjectStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Foto de perfil. O arquivo vai para o MinIO e o banco guarda só a chave do objeto.
 *
 * <p>Cada upload grava um objeto novo (chave com UUID) em vez de sobrescrever: a URL antiga, que
 * pode estar no cache do navegador, nunca mostra a foto errada, e o objeto pode ser cacheado como
 * imutável. Ordem: grava o arquivo, troca a chave no banco, apaga o arquivo anterior. Se o banco
 * falha, o arquivo novo é apagado; se apagar o anterior falha, sobra um arquivo órfão (só log).
 */
@Service
public class AvatarService {

	private static final Logger log = LoggerFactory.getLogger(AvatarService.class);

	// objeto nunca muda (chave nova a cada upload): o navegador reaproveita enquanto a URL valer
	private static final String CACHE_CONTROL = "private, max-age=86400, immutable";

	private static final Duration URL_TTL = Duration.ofHours(24);

	// a mesma URL é devolvida por metade da validade: o <img> não baixa a foto de novo a cada
	// GET /me/profile (assinar de novo muda a URL) e quem a recebe tem pelo menos 12h para usar
	private static final Duration URL_REUSE = URL_TTL.dividedBy(2);

	private static final int MAX_CACHED_URLS = 10_000;

	private record SignedUrl(URI url, Instant reuseUntil) {
	}

	private final UserProfileRepository repository;

	private final ObjectStorage storage;

	private final TransactionTemplate transaction;

	private final String bucket;

	// LRU por chave do objeto: só as fotos vistas recentemente
	private final Map<String, SignedUrl> urls = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, SignedUrl> eldest) {
			return size() > MAX_CACHED_URLS;
		}
	});

	AvatarService(UserProfileRepository repository, ObjectStorage storage, TransactionTemplate transaction,
			@Value("${gabarit.storage.avatars-bucket}") String bucket) {
		this.repository = repository;
		this.storage = storage;
		this.transaction = transaction;
		this.bucket = bucket;
	}

	/**
	 * Troca a foto do usuário e devolve a URL da nova.
	 *
	 * @throws BadRequestException não é JPEG/PNG válido
	 */
	public URI upload(String userId, byte[] content) {
		byte[] image = AvatarImage.normalize(content);
		String key = "%s/%s.jpg".formatted(userId, UUID.randomUUID());
		storage.put(bucket, key, image, AvatarImage.CONTENT_TYPE, CACHE_CONTROL);

		String previous;
		try {
			previous = transaction.execute(status -> {
				UserProfile profile = repository.findForUpdate(userId).orElseGet(() -> new UserProfile(userId));
				String old = profile.changeAvatar(key);
				repository.save(profile);
				return old;
			});
		}
		catch (RuntimeException e) {
			deleteQuietly(key);
			throw e;
		}
		deleteQuietly(previous);
		return url(key);
	}

	/** Tira a foto. Sem foto, não faz nada. */
	public void remove(String userId) {
		String previous = transaction.execute(status -> repository.findForUpdate(userId)
			.map(profile -> profile.changeAvatar(null))
			.orElse(null));
		deleteQuietly(previous);
	}

	/** URL temporária para o navegador ler a foto; {@code null} se não há foto. */
	public URI url(String avatarKey) {
		if (avatarKey == null) {
			return null;
		}
		Instant now = Instant.now();
		SignedUrl cached = urls.get(avatarKey);
		if (cached != null && now.isBefore(cached.reuseUntil())) {
			return cached.url();
		}
		URI url = storage.presignedGet(bucket, avatarKey, URL_TTL);
		urls.put(avatarKey, new SignedUrl(url, now.plus(URL_REUSE)));
		return url;
	}

	private void deleteQuietly(String key) {
		if (key == null) {
			return;
		}
		urls.remove(key);
		try {
			storage.delete(bucket, key);
		}
		catch (RuntimeException e) {
			log.warn("não foi possível apagar a foto {} do bucket {}: arquivo órfão", key, bucket, e);
		}
	}

}
