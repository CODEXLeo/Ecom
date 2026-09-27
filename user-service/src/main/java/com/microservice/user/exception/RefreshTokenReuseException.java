package com.microservice.user.exception;

public class RefreshTokenReuseException extends RuntimeException {

    /**
	 * 
	 */
	private static final long serialVersionUID = 8161974286604392953L;

	public RefreshTokenReuseException() {
        super("Refresh token reuse detected");
    }
}