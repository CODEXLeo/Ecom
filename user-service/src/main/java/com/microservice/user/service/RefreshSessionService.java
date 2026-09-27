package com.microservice.user.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.microservice.user.entity.RefreshSession;
import com.microservice.user.entity.User;
import com.microservice.user.exception.InvalidRefreshTokenException;
import com.microservice.user.exception.RefreshTokenReuseException;
import com.microservice.user.repository.RefreshSessionRepository;

@Service
public class RefreshSessionService {

    private static final int REFRESH_TOKEN_BYTES = 32;

    private final RefreshSessionRepository refreshSessionRepository;

    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshSessionService(RefreshSessionRepository refreshSessionRepository) {
        this.refreshSessionRepository = refreshSessionRepository;
    }

    /**
     * Creates a completely new refresh-token family.
     *
     * Used when the user logs in.
     */
    @Transactional
    public CreatedRefreshSession createSession(User user, Duration refreshTokenLifetime, String userAgent, String ipAddress) {
        String rawRefreshToken = generateRefreshToken();
        String tokenHash = hashToken(rawRefreshToken);
        UUID familyId = UUID.randomUUID();
        Instant expiresAt = Instant.now().plus(refreshTokenLifetime);
        RefreshSession refreshSession = RefreshSession.create(user, tokenHash, familyId, expiresAt, userAgent,ipAddress);
        RefreshSession savedSession = refreshSessionRepository.save(refreshSession);

        return new CreatedRefreshSession(savedSession, rawRefreshToken);
    }

    /**
     * Rotates an existing refresh token.
     *
     * The entire operation runs inside one transaction so that
     * the pessimistic database lock acquired by
     * findByTokenHashForUpdate() remains held until the operation
     * has completed.
     *
     * If a previously revoked refresh token is presented again,
     * the entire refresh-token family is revoked and the
     * RefreshTokenReuseException is thrown.
     *
     * RefreshTokenReuseException intentionally does NOT cause
     * this transaction to roll back because the family revocation
     * must be committed.
     */
    @Transactional(noRollbackFor = RefreshTokenReuseException.class)
    public CreatedRefreshSession rotateRefreshToken(
            String rawRefreshToken,
            Duration refreshTokenLifetime,
            String userAgent,
            String ipAddress) {

        Instant now = Instant.now();
        String tokenHash = hashToken(rawRefreshToken);

        /*
         * PESSIMISTIC_WRITE locks this database row for the
         * duration of this transaction.
         */
        RefreshSession currentSession = refreshSessionRepository.findByTokenHashForUpdate(tokenHash).orElseThrow(InvalidRefreshTokenException::new);

        /*
         * A previously revoked refresh token has been presented
         * again.
         *
         * This indicates refresh-token reuse.
         *
         * Revoke the entire family in THIS transaction.
         *
         * noRollbackFor ensures that the revocation is committed
         * even though RefreshTokenReuseException is thrown.
         */
        if (currentSession.isRevoked()) {
            revokeFamilyInternal( currentSession.getFamilyId(), now);
            throw new RefreshTokenReuseException();
        }

        /*
         * An expired refresh token cannot be rotated.
         */
        if (currentSession.isExpired(now)) {
            throw new InvalidRefreshTokenException();
        }

        /*
         * Generate a completely new random refresh token.
         */
        String newRawRefreshToken = generateRefreshToken();

        String newTokenHash = hashToken(newRawRefreshToken);

        /*
         * The replacement session belongs to the same family.
         */
        RefreshSession newSession = RefreshSession.create(currentSession.getUser(), newTokenHash, currentSession.getFamilyId(), now.plus(refreshTokenLifetime),
        		userAgent, ipAddress);

        /*
         * Save the replacement first so that we have its UUID.
         */
        RefreshSession savedNewSession = refreshSessionRepository.save(newSession);

        /*
         * Revoke the old refresh token and record which session
         * replaced it.
         */
        currentSession.rotateTo(savedNewSession.getSessionId(), now);

        /*
         * currentSession is already a managed JPA entity because
         * it was loaded inside this transaction.
         *
         * JPA dirty checking will persist the changes at commit.
         */

        return new CreatedRefreshSession(savedNewSession, newRawRefreshToken);
    }

    /**
     * Revoke one refresh session.
     */
    @Transactional
    public void revoke(RefreshSession session) {
        session.revoke(Instant.now());
        refreshSessionRepository.save(session);
    }

    /**
     * Revoke every session belonging to the same refresh-token
     * family.
     *
     * Used for refresh-token reuse detection and can also be
     * used by other application flows that need to revoke an
     * entire family.
     */
    @Transactional
    public void revokeFamily(UUID familyId) {
        revokeFamilyInternal(familyId, Instant.now());
    }

    /**
     * Internal family-revocation operation.
     *
     * This method intentionally has no @Transactional annotation.
     *
     * When called from rotateRefreshToken(), it participates in
     * that existing transaction, allowing the family revocation
     * and the locked refresh-session operation to commit together.
     */
    private void revokeFamilyInternal(UUID familyId, Instant now) {

        List<RefreshSession> sessions = refreshSessionRepository.findAllByFamilyIdForUpdate(familyId);

        for (RefreshSession session : sessions) {
            session.revoke(now);
        }

        refreshSessionRepository.saveAll(sessions);
    }

    /**
     * Revoke every active refresh session belonging to a user.
     *
     * Useful for "logout all devices".
     */
    @Transactional
    public void revokeAllUserSessions(UUID userId) {
        List<RefreshSession> sessions = refreshSessionRepository.findAllActiveByUserIdForUpdate(userId);
        Instant now = Instant.now();
        for (RefreshSession session : sessions) {
            session.revoke(now);
        }

        refreshSessionRepository.saveAll(sessions);
    }
    
    @Transactional
    public void revokeByRawToken(String rawRefreshToken) {

        String tokenHash = hashToken(rawRefreshToken);

        RefreshSession session =
                refreshSessionRepository
                        .findByTokenHashForUpdate(tokenHash)
                        .orElse(null);

        /*
         * Logout should be idempotent.
         *
         * If the token is already invalid/unknown,
         * there is nothing to revoke.
         */
        if (session == null) {
            return;
        }

        session.revoke(Instant.now());
    }

    /**
     * Generates a cryptographically secure opaque refresh token.
     *
     * 32 random bytes = 256 bits of entropy.
     */
    private String generateRefreshToken() {
        byte[] randomBytes = new byte[REFRESH_TOKEN_BYTES];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    /**
     * Hashes the raw refresh token using SHA-256.
     *
     * Only the hash is stored in the database.
     *
     * The raw refresh token exists only in application memory
     * and is eventually sent to the client through the
     * HttpOnly cookie.
     */
    private String hashToken(String rawRefreshToken) {

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(rawRefreshToken.getBytes(StandardCharsets.UTF_8));

            StringBuilder hex = new StringBuilder(hash.length * 2);

            for (byte b : hash) {
                hex.append(String.format("%02x", b & 0xff));
            }

            return hex.toString();

        } catch (NoSuchAlgorithmException exception) {

            /*
             * SHA-256 is required by a standard Java runtime.
             *
             * Therefore this indicates a broken runtime/provider
             * configuration rather than invalid user input.
             */
            throw new IllegalStateException("SHA-256 algorithm is not available", exception);
        }
    }

    /**
     * Returned after creating or rotating a refresh session.
     *
     * The raw refresh token should never be persisted.
     */
    public record CreatedRefreshSession(RefreshSession session, String rawRefreshToken) {
    }
}