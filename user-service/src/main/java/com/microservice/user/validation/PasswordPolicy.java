package com.microservice.user.validation;

public final class PasswordPolicy {

    private PasswordPolicy() {
    }

    /**
     * Application-wide password policy:
     * 8-128 characters, at least one lowercase letter, one uppercase
     * letter, one digit, and one non-alphanumeric character.
     */
    public static final String REGEX =
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z\\d]).{8,128}$";
}
