package com.microservice.user.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "refresh_sessions", indexes = {
		@Index(name = "idx_refresh_session_user_id", columnList = "user_id"), 
		@Index(name = "idx_refresh_session_family_id", columnList = "family_id")
		}
)
public class RefreshSession extends BaseEntity {

    @jakarta.persistence.Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID sessionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /*
     * We NEVER store the raw refresh token.
     *
     * The browser receives the raw token.
     * The database stores only its SHA-256 hash.
     */
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    /*
     * All refresh sessions created during one
     * refresh-token rotation chain belong to the
     * same family.
     *
     * This allows us to detect refresh-token reuse
     * and revoke the entire family.
     */
    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    /*
     * NULL = currently active.
     *
     * Non-null = revoked.
     */
    @Column(name = "revoked_at")
    private Instant revokedAt;

    /*
     * When this session is rotated, this points
     * to the newly created session.
     */
    @Column(name = "replaced_by_session_id")
    private UUID replacedBySessionId;

    /*
     * Useful for device/session tracking.
     *
     * These are metadata, NOT authentication credentials.
     */
    @Column(name = "user_agent", length = 1000)
    private String userAgent;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    protected RefreshSession() {
        // Required by JPA.
    }

    private RefreshSession(User user, String tokenHash, UUID familyId, Instant expiresAt, String userAgent,String ipAddress) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.familyId = familyId;
        this.expiresAt = expiresAt;
        this.userAgent = userAgent;
        this.ipAddress = ipAddress;
    }

    public static RefreshSession create(User user, String tokenHash, UUID familyId, Instant expiresAt, String userAgent, String ipAddress) {
        return new RefreshSession(user, tokenHash, familyId, expiresAt, userAgent, ipAddress);
    }

    public void rotateTo(UUID newSessionId, Instant now) {
        this.revokedAt = now;
        this.lastUsedAt = now;
        this.replacedBySessionId = newSessionId;
    }

    public void revoke(Instant now) {
        if (this.revokedAt == null) {
            this.revokedAt = now;
        }
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public User getUser() {
        return user;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public UUID getFamilyId() {
        return familyId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public UUID getReplacedBySessionId() {
        return replacedBySessionId;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public String getIpAddress() {
        return ipAddress;
    }
}