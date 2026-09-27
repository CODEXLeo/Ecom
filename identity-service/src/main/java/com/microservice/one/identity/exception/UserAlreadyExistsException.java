package com.microservice.one.identity.exception;

public class UserAlreadyExistsException extends RuntimeException {

    /**
	 * 
	 */
	private static final long serialVersionUID = -2713670117423621625L;

	public UserAlreadyExistsException(String message) {
        super(message);
    }

}