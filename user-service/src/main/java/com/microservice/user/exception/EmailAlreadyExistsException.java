package com.microservice.user.exception;

public class EmailAlreadyExistsException extends RuntimeException {
	/**
	 * 
	 */
	private static final long serialVersionUID = 2298998129435438843L;

	public EmailAlreadyExistsException() {
		super("An account is already registered with this email");
	}
}
