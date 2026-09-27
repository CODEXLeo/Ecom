package com.microservice.user.security.auth;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "security.authentication")
public class AuthenticationProperties {

    /*
     * Number of consecutive failed login attempts before
     * the account is temporarily locked.
     */
    private int maximumFailedLoginAttempts = 5;

    /*
     * How long the temporary account lock remains active.
     */
    private Duration accountLockDuration = Duration.ofMinutes(15);

    public int getMaximumFailedLoginAttempts() {
        return maximumFailedLoginAttempts;
    }

    public void setMaximumFailedLoginAttempts(int maximumFailedLoginAttempts) {
        this.maximumFailedLoginAttempts = maximumFailedLoginAttempts;
    }

    public Duration getAccountLockDuration() {
        return accountLockDuration;
    }

    public void setAccountLockDuration(Duration accountLockDuration) {
        this.accountLockDuration = accountLockDuration;
    }
}