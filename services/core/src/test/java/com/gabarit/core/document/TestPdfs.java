package com.gabarit.core.document;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/** PDFs de teste e o envio como o navegador faz (PUT na URL pré-assinada). */
public final class TestPdfs {

	private static final HttpClient HTTP = HttpClient.newHttpClient();

	private TestPdfs() {
	}

	/** PDF válido; {@code seed} muda o conteúdo (e o hash) sem mudar as páginas. */
	public static byte[] pdf(int pages, String seed) {
		try (PDDocument pdf = new PDDocument(); var out = new ByteArrayOutputStream()) {
			for (int i = 0; i < pages; i++) {
				pdf.addPage(new PDPage());
			}
			pdf.getDocumentInformation().setTitle(seed);
			pdf.save(out);
			return out.toByteArray();
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	public static byte[] withPassword() {
		try (PDDocument pdf = new PDDocument(); var out = new ByteArrayOutputStream()) {
			pdf.addPage(new PDPage());
			var policy = new StandardProtectionPolicy("dono", "senha", new AccessPermission());
			policy.setEncryptionKeyLength(128);
			pdf.protect(policy);
			pdf.save(out);
			return out.toByteArray();
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** @return status HTTP do MinIO */
	public static int put(URI url, String contentType, byte[] content) {
		try {
			return HTTP.send(HttpRequest.newBuilder(url)
				.header("Content-Type", contentType)
				.PUT(HttpRequest.BodyPublishers.ofByteArray(content))
				.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
		}
		catch (IOException | InterruptedException e) {
			throw new IllegalStateException(e);
		}
	}

	public static HttpResponse<byte[]> get(URI url) {
		try {
			return HTTP.send(HttpRequest.newBuilder(url).build(), HttpResponse.BodyHandlers.ofByteArray());
		}
		catch (IOException | InterruptedException e) {
			throw new IllegalStateException(e);
		}
	}

}
