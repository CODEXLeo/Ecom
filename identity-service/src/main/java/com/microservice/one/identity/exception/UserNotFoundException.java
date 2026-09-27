package com.microservice.one.identity.exception;

public class UserNotFoundException extends RuntimeException {

    /**
	 * 
	 */
	private static final long serialVersionUID = -8432583375114893544L;

	public UserNotFoundException(String message) {
        super(message);
    }

}