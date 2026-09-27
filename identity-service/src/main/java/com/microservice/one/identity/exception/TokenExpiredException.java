package com.microservice.one.identity.exception;

public class TokenExpiredException extends RuntimeException {

    /**
	 * 
	 */
	private static final long serialVersionUID = 1849994041308162559L;

	public TokenExpiredException(String message) {
        super(message);
    }

}