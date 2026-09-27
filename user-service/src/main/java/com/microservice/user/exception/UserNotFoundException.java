package com.microservice.user.exception;

public class UserNotFoundException extends RuntimeException {

    /**
	 * 
	 */
	private static final long serialVersionUID = 6680975744034945049L;

	public UserNotFoundException() {
        super("User not found");
    }
}