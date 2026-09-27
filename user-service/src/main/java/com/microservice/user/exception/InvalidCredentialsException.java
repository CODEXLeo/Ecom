package com.microservice.user.exception;

public class InvalidCredentialsException extends RuntimeException{
	/**
	 * 
	 */
	private static final long serialVersionUID = -5429079470826116957L;

	public InvalidCredentialsException() {
		super("Invalid email or password");
	}
}
