package com.microservice.user.entity;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.microservice.user.enums.Role;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "email", nullable = false, length = 254, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(nullable = false)
    private boolean enabled = true;

    /*
     * =========================================================
     * ACCOUNT LOCKOUT STATE
     * =========================================================
     */

    @Column(name = "account_locked", nullable = false)
    private boolean accountLocked = false;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts = 0;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "lock_expires_at")
    private Instant lockExpiresAt;

    /*
     * Required by JPA/Hibernate.
     */
    protected User() {
    }

    /*
     * Private constructor.
     *
     * Role can only be selected by controlled factory methods.
     */
    private User(String firstName, String lastName, String email, String passwordHash, Role role) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
    }

    /*
     * =========================================================
     * USER CREATION
     * =========================================================
     */

    /*
     * Public registration.
     *
     * A normal registration can NEVER choose its role.
     */
    public static User create(String firstName, String lastName, String email, String passwordHash) {
        return new User(firstName, lastName, email, passwordHash, Role.ROLE_USER);
    }

    /*
     * Initial/bootstrap administrator creation.
     *
     * This is deliberately separate from public registration.
     */
    public static User createAdmin(String firstName,String lastName,String email,String passwordHash) {
        return new User(firstName, lastName, email, passwordHash, Role.ROLE_ADMIN);
    }

    /*
     * =========================================================
     * PROFILE
     * =========================================================
     */

    public void updateProfile(String firstName, String lastName) {
        this.firstName = firstName;
        this.lastName = lastName;
    }

    /*
     * =========================================================
     * PASSWORD
     * =========================================================
     */

    public void changePassword(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    /*
     * =========================================================
     * ACCOUNT STATUS
     * =========================================================
     */

    public void enable() {
        this.enabled = true;
    }

    public void disable() {
        this.enabled = false;
    }

    /*
     * =========================================================
     * ACCOUNT LOCKOUT
     * =========================================================
     */

    /**
     * Records one failed login attempt.
     *
     * The caller must execute this while the User row is
     * protected by a pessimistic database lock.
     */
    public void recordFailedLoginAttempt(int maximumFailedLoginAttempts, Instant now, Duration accountLockDuration) {
        failedLoginAttempts++;
        if (failedLoginAttempts >= maximumFailedLoginAttempts) {
            accountLocked = true;
            lockedAt = now;
            lockExpiresAt = now.plus(accountLockDuration);
        }
    }

    /**
     * Automatically unlocks a temporary account lock when
     * the lock expiration time has been reached.
     *
     * @return true if the account was unlocked
     */
    public boolean unlockIfExpired(Instant now) {
        if (!accountLocked) {
            return false;
        }

        /*
         * Fail closed if the database contains an invalid
         * locked state without an expiration time.
         */
        if (lockExpiresAt == null) {
            return false;
        }

        if (lockExpiresAt.isAfter(now)) {
            return false;
        }
        accountLocked = false;
        failedLoginAttempts = 0;
        lockedAt = null;
        lockExpiresAt = null;
        return true;
    }

    /**
     * Clears all failed-login state after successful login
     * or administrative unlock.
     */
    public void resetFailedLoginAttempts() {

        failedLoginAttempts = 0;
        accountLocked = false;
        lockedAt = null;
        lockExpiresAt = null;
    }

    /*
     * =========================================================
     * GETTERS
     * =========================================================
     */

    public UUID getUserId() {
        return userId;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isAccountLocked() {
        return accountLocked;
    }

    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    public Instant getLockedAt() {
        return lockedAt;
    }

    public Instant getLockExpiresAt() {
        return lockExpiresAt;
    }
}