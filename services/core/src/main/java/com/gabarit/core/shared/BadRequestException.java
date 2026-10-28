package com.gabarit.core.shared;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/** Requisição com dados inválidos. Vira 400 no formato ProblemDetail (RFC 9457). */
public class BadRequestException extends ErrorResponseException {

	public BadRequestException(String detail) {
		super(HttpStatus.BAD_REQUEST, ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail), null);
	}

}
