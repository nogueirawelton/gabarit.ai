package com.gabarit.core.shared;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.MetadataDirective;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;

/**
 * Arquivos no MinIO (API S3). Buckets, policies e o usuário do core são criados pela infra
 * (infra/minio/init.sh), não aqui.
 *
 * <p>Dois endpoints: leitura e escrita pela rede interna ({@code url}); URLs pré-assinadas com a URL
 * pública ({@code public-url}), porque a assinatura inclui o host: assinada com minio:9000, o
 * navegador não consegue usar. Assinar não acessa a rede.
 */
@Component
public class ObjectStorage {

	// o MinIO ignora a região, mas a assinatura precisa de uma
	private static final Region REGION = Region.US_EAST_1;

	private final S3Client client;

	private final S3Presigner presigner;

	ObjectStorage(@Value("${gabarit.storage.url}") URI url, @Value("${gabarit.storage.public-url}") URI publicUrl,
			@Value("${gabarit.storage.access-key}") String accessKey,
			@Value("${gabarit.storage.secret-key}") String secretKey) {
		var credentials = StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
		// MinIO: bucket no caminho (minio:9000/avatars/...), não no subdomínio
		var pathStyle = S3Configuration.builder().pathStyleAccessEnabled(true).build();
		this.client = S3Client.builder()
			.endpointOverride(url)
			.region(REGION)
			.credentialsProvider(credentials)
			.serviceConfiguration(pathStyle)
			.httpClient(UrlConnectionHttpClient.create())
			.build();
		this.presigner = S3Presigner.builder()
			.endpointOverride(publicUrl)
			.region(REGION)
			.credentialsProvider(credentials)
			.serviceConfiguration(pathStyle)
			.build();
	}

	public void put(String bucket, String key, byte[] content, String contentType, String cacheControl) {
		client.putObject(r -> r.bucket(bucket).key(key).contentType(contentType).cacheControl(cacheControl),
				RequestBody.fromBytes(content));
	}

	/** Tamanho do objeto, ou vazio se ele não existe. */
	public Optional<Long> size(String bucket, String key) {
		try {
			return Optional.of(client.headObject(r -> r.bucket(bucket).key(key)).contentLength());
		}
		catch (NoSuchKeyException e) {
			return Optional.empty();
		}
	}

	/** Conteúdo do objeto em stream (feche depois de ler). */
	public InputStream read(String bucket, String key) {
		return client.getObject(r -> r.bucket(bucket).key(key));
	}

	/** Cópia dentro do MinIO (o conteúdo não passa pelo core), com o Content-Type trocado. */
	public void copy(String bucket, String fromKey, String toKey, String contentType) {
		client.copyObject(r -> r.sourceBucket(bucket)
			.sourceKey(fromKey)
			.destinationBucket(bucket)
			.destinationKey(toKey)
			.contentType(contentType)
			.metadataDirective(MetadataDirective.REPLACE));
	}

	/** Apagar o que não existe não é erro (S3). */
	public void delete(String bucket, String key) {
		client.deleteObject(r -> r.bucket(bucket).key(key));
	}

	/** URL de leitura que o navegador usa direto, sem token, até expirar. */
	public URI presignedGet(String bucket, String key, Duration ttl) {
		return URI.create(presigner
			.presignGetObject(r -> r.signatureDuration(ttl).getObjectRequest(g -> g.bucket(bucket).key(key)))
			.url()
			.toString());
	}

	/**
	 * URL para o navegador enviar o arquivo direto ao MinIO (sem passar pelo core), até expirar. O
	 * Content-Type entra na assinatura: o envio tem de mandar exatamente esse.
	 */
	public URI presignedPut(String bucket, String key, String contentType, Duration ttl) {
		return URI.create(presigner
			.presignPutObject(r -> r.signatureDuration(ttl)
				.putObjectRequest(p -> p.bucket(bucket).key(key).contentType(contentType)))
			.url()
			.toString());
	}

	@PreDestroy
	void close() {
		presigner.close();
		client.close();
	}

}
