package com.microservice.user.exception;

public class InvalidCurrentPasswordException extends RuntimeException {

    /**
	 * 
	 */
	private static final long serialVersionUID = -1415648049561597092L;

	public InvalidCurrentPasswordException() {
        super("Current password is incorrect");
    }
}