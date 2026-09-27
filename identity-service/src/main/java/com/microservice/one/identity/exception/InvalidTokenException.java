package com.microservice.one.identity.exception;

public class InvalidTokenException extends RuntimeException {

    /**
	 * 
	 */
	private static final long serialVersionUID = 5287176595883929531L;

	public InvalidTokenException(String message) {
        super(message);
    }

}