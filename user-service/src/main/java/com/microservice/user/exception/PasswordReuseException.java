package com.microservice.user.exception;

public class PasswordReuseException extends RuntimeException {

    /**
	 * 
	 */
	private static final long serialVersionUID = 1774603785677590300L;

	public PasswordReuseException() {
        super("New password must be different from the current password");
    }
}