package com.microservice.one.identity.entity;

import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;

import com.microservice.one.identity.enums.Role;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Represents an authenticated user.
 */
@Entity
@Table(name = "users")
public class User extends BaseEntity {

    @Id
    @UuidGenerator
    @Column(
            name = "user_id",
            nullable = false,
            updatable = false,
            columnDefinition = "BINARY(16)")
    private UUID userId;

    @Column(
            name = "first_name",
            nullable = false,
            length = 100)
    private String firstName;

    @Column(
            name = "last_name",
            nullable = false,
            length = 100)
    private String lastName;

    @Column(
            name = "email",
            nullable = false,
            unique = true,
            length = 150)
    private String email;

    @Column(
            name = "password_hash",
            nullable = false,
            length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(
            nullable = false,
            length = 20)
    private Role role;

    @Column(
            name = "enabled",
            nullable = false)
    private boolean enabled = true;

    @Column(
            name = "account_non_locked",
            nullable = false)
    private boolean accountNonLocked = true;

    public User() {
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isAccountNonLocked() {
        return accountNonLocked;
    }

    public void setAccountNonLocked(boolean accountNonLocked) {
        this.accountNonLocked = accountNonLocked;
    }

}