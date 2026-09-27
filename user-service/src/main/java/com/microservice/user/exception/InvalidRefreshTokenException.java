package com.microservice.user.exception;

public class InvalidRefreshTokenException extends RuntimeException {

    /**
	 * 
	 */
	private static final long serialVersionUID = -4464174398413853112L;

	public InvalidRefreshTokenException() {
        super("Invalid refresh token");
    }
}