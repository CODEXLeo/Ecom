package com.microservice.products.inventory.service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.microservice.products.config.InventoryReservationProperties;
import com.microservice.products.exception.IdempotencyKeyConflictException;
import com.microservice.products.exception.InsufficientStockException;
import com.microservice.products.exception.InvalidReservationStateException;
import com.microservice.products.exception.ProductNotFoundException;
import com.microservice.products.exception.ReservationNotFoundException;
import com.microservice.products.inventory.dto.request.ReservationRequest;
import com.microservice.products.inventory.dto.response.ReservationResponse;
import com.microservice.products.inventory.entity.InventoryReservation;
import com.microservice.products.inventory.entity.ReservationStatus;
import com.microservice.products.inventory.repository.InventoryReservationRepository;
import com.microservice.products.product.entity.Product;
import com.microservice.products.product.entity.ProductStatus;
import com.microservice.products.product.repository.ProductRepository;

@Service
public class InventoryServiceImpl implements InventoryService {

    private final ProductRepository productRepository;

    private final InventoryReservationRepository reservationRepository;

    private final TransactionTemplate transactionTemplate;

    private final Duration reservationDuration;

    public InventoryServiceImpl(
            ProductRepository productRepository,
            InventoryReservationRepository reservationRepository,
            TransactionTemplate transactionTemplate,
            InventoryReservationProperties reservationProperties) {

        this.productRepository = productRepository;
        this.reservationRepository = reservationRepository;
        this.transactionTemplate = transactionTemplate;

        this.reservationDuration =
                reservationProperties.duration();
    }

    /*
     * ============================================================
     * RESERVE
     * ============================================================
     */

    @Override
    public ReservationResponse reserve(
            ReservationRequest request,
            String idempotencyKey) {

        String normalizedKey =
                normalizeIdempotencyKey(idempotencyKey);

        /*
         * Fast path for normal retries.
         */
        InventoryReservation existing =
                reservationRepository
                        .findByIdempotencyKey(normalizedKey)
                        .orElse(null);

        if (existing != null) {

            validateIdempotentRetry(
                    existing,
                    request
            );

            return toResponse(existing);
        }

        return reserveInTransaction(
                request,
                normalizedKey
        );
    }

    private ReservationResponse reserveInTransaction(
            ReservationRequest request,
            String idempotencyKey) {

        try {

            ReservationResponse response =
                    transactionTemplate.execute(status -> {

                        /*
                         * Check again inside the transaction.
                         *
                         * Two concurrent requests may both miss
                         * the initial lookup.
                         */
                        InventoryReservation existing =
                                reservationRepository
                                        .findByIdempotencyKey(
                                                idempotencyKey
                                        )
                                        .orElse(null);

                        if (existing != null) {

                            validateIdempotentRetry(
                                    existing,
                                    request
                            );

                            return toResponse(existing);
                        }

                        return createReservation(
                                request,
                                idempotencyKey
                        );
                    });

            if (response == null) {

                throw new IllegalStateException(
                        "Unable to create inventory reservation"
                );
            }

            return response;

        } catch (DataIntegrityViolationException exception) {

            /*
             * The database UNIQUE constraint on idempotency_key
             * is the final concurrency protection.
             *
             * If another transaction won the race, retrieve
             * its reservation.
             */
            InventoryReservation existing =
                    reservationRepository
                            .findByIdempotencyKey(idempotencyKey)
                            .orElse(null);

            if (existing == null) {
                throw exception;
            }

            validateIdempotentRetry(
                    existing,
                    request
            );

            return toResponse(existing);
        }
    }

    private ReservationResponse createReservation(
            ReservationRequest request,
            String idempotencyKey) {

        /*
         * Pessimistic lock on the product row.
         *
         * This serializes concurrent inventory modifications
         * for the same product.
         */
        Product product =
                productRepository
                        .findByIdForUpdate(
                                request.productId()
                        )
                        .orElseThrow(() ->
                                new ProductNotFoundException(
                                        request.productId()
                                )
                        );

        if (product.getStatus() != ProductStatus.ACTIVE) {

            throw new InsufficientStockException(
                    "Product is not available for reservation: "
                            + request.productId()
            );
        }

        int requestedQuantity =
                request.quantity();

        if (product.getStockQuantity()
                < requestedQuantity) {

            throw new InsufficientStockException(
                    "Insufficient stock for product: "
                            + request.productId()
            );
        }

        /*
         * Reserve the stock immediately.
         */
        product.updateStock(
                product.getStockQuantity()
                        - requestedQuantity
        );

        Instant expiresAt =
                Instant.now()
                        .plus(reservationDuration);

        InventoryReservation reservation =
                new InventoryReservation(
                        request.orderId(),
                        request.productId(),
                        requestedQuantity,
                        idempotencyKey,
                        expiresAt
                );

        /*
         * Force the INSERT so the UNIQUE constraint is checked
         * while this transaction is still active.
         */
        reservationRepository.saveAndFlush(
                reservation
        );

        return toResponse(reservation);
    }

    /*
     * ============================================================
     * COMMIT
     * ============================================================
     *
     * RESERVED -> COMMITTED
     *
     * Stock is NOT restored.
     */

    @Override
    @Transactional(
            noRollbackFor = InvalidReservationStateException.class
    )
    public ReservationResponse commit(
            UUID reservationId) {

        InventoryReservation reservation =
                reservationRepository
                        .findByIdForUpdate(reservationId)
                        .orElseThrow(() ->
                                new ReservationNotFoundException(
                                        reservationId
                                )
                        );

        /*
         * Idempotent commit.
         */
        if (reservation.getStatus()
                == ReservationStatus.COMMITTED) {

            return toResponse(reservation);
        }

        /*
         * RELEASED / EXPIRED cannot be committed.
         */
        if (reservation.getStatus()
                != ReservationStatus.RESERVED) {

            throw new InvalidReservationStateException(
                    "Reservation cannot be committed because "
                            + "its current status is "
                            + reservation.getStatus()
            );
        }

        /*
         * Check expiry at commit time.
         *
         * The scheduler is not the source of truth.
         */
        if (!reservation.getExpiresAt()
                .isAfter(Instant.now())) {

            /*
             * Restore the stock and persist EXPIRED.
             */
            releaseStock(reservation);

            reservation.expire();

            throw new InvalidReservationStateException(
                    "Reservation has expired: "
                            + reservationId
            );
        }

        reservation.commit();

        return toResponse(reservation);
    }

    /*
     * ============================================================
     * RELEASE
     * ============================================================
     *
     * RESERVED -> RELEASED
     */

    @Override
    @Transactional
    public ReservationResponse release(
            UUID reservationId) {

        InventoryReservation reservation =
                reservationRepository
                        .findByIdForUpdate(reservationId)
                        .orElseThrow(() ->
                                new ReservationNotFoundException(
                                        reservationId
                                )
                        );

        /*
         * Idempotent release.
         */
        if (reservation.getStatus()
                == ReservationStatus.RELEASED) {

            return toResponse(reservation);
        }

        /*
         * Committed inventory cannot be released.
         */
        if (reservation.getStatus()
                == ReservationStatus.COMMITTED) {

            throw new InvalidReservationStateException(
                    "Committed reservation cannot be released: "
                            + reservationId
            );
        }

        /*
         * Expiration has already restored the stock.
         */
        if (reservation.getStatus()
                == ReservationStatus.EXPIRED) {

            return toResponse(reservation);
        }

        /*
         * Only RESERVED should reach this point.
         */
        if (reservation.getStatus()
                != ReservationStatus.RESERVED) {

            throw new InvalidReservationStateException(
                    "Reservation cannot be released because "
                            + "its current status is "
                            + reservation.getStatus()
            );
        }

        releaseStock(reservation);

        reservation.release();

        return toResponse(reservation);
    }

    /*
     * ============================================================
     * EXPIRATION
     * ============================================================
     */

    @Override
    @Scheduled(
            fixedDelayString =
                    "${inventory.reservation.expiry-check-delay-ms:60000}"
    )
    @Transactional
    public int expireReservations() {

        Instant now =
                Instant.now();

        var expiredReservations =
                reservationRepository
                        .findByStatusAndExpiresAtBefore(
                                ReservationStatus.RESERVED,
                                now
                        );

        int expiredCount = 0;

        for (InventoryReservation reservation :
                expiredReservations) {

            /*
             * Re-read with a pessimistic lock.
             *
             * This prevents expiration from racing with
             * commit/release.
             */
            InventoryReservation lockedReservation =
                    reservationRepository
                            .findByIdForUpdate(
                                    reservation.getReservationId()
                            )
                            .orElse(null);

            if (lockedReservation == null) {
                continue;
            }

            if (lockedReservation.getStatus()
                    != ReservationStatus.RESERVED) {

                continue;
            }

            if (lockedReservation.getExpiresAt()
                    .isAfter(now)) {

                continue;
            }

            releaseStock(
                    lockedReservation
            );

            lockedReservation.expire();

            expiredCount++;
        }

        return expiredCount;
    }

    /*
     * ============================================================
     * RESTORE STOCK
     * ============================================================
     */

    private void releaseStock(
            InventoryReservation reservation) {

        /*
         * Lock the product row using the same lock used
         * during reservation.
         */
        Product product =
                productRepository
                        .findByIdForUpdate(
                                reservation.getProductId()
                        )
                        .orElseThrow(() ->
                                new ProductNotFoundException(
                                        reservation.getProductId()
                                )
                        );

        product.updateStock(
                product.getStockQuantity()
                        + reservation.getQuantity()
        );
    }

    /*
     * ============================================================
     * IDEMPOTENCY VALIDATION
     * ============================================================
     *
     * A key identifies one logical reservation attempt.
     *
     * same key + same request
     *      -> return existing reservation
     *
     * same key + different request
     *      -> 409
     */

    private void validateIdempotentRetry(
            InventoryReservation existing,
            ReservationRequest request) {

        boolean sameOrder =
                existing.getOrderId()
                        .equals(request.orderId());

        boolean sameProduct =
                existing.getProductId()
                        .equals(request.productId());

        boolean sameQuantity =
                existing.getQuantity()
                        .equals(request.quantity());

        if (!sameOrder
                || !sameProduct
                || !sameQuantity) {

            throw new IdempotencyKeyConflictException(
                    "Idempotency-Key was already used "
                            + "with a different reservation request"
            );
        }
    }

    /*
     * ============================================================
     * RESPONSE
     * ============================================================
     */

    private ReservationResponse toResponse(
            InventoryReservation reservation) {

        return new ReservationResponse(
                reservation.getReservationId(),
                reservation.getOrderId(),
                reservation.getProductId(),
                reservation.getQuantity(),
                reservation.getStatus(),
                reservation.getExpiresAt()
        );
    }

    /*
     * ============================================================
     * IDEMPOTENCY KEY NORMALIZATION
     * ============================================================
     */

    private String normalizeIdempotencyKey(
            String idempotencyKey) {

        if (idempotencyKey == null) {

            throw new IdempotencyKeyConflictException(
                    "Idempotency-Key header is required"
            );
        }

        String normalized =
                idempotencyKey.trim();

        if (normalized.isEmpty()) {

            throw new IdempotencyKeyConflictException(
                    "Idempotency-Key header cannot be blank"
            );
        }

        if (normalized.length() > 200) {

            throw new IdempotencyKeyConflictException(
                    "Idempotency-Key must not exceed "
                            + "200 characters"
            );
        }

        return normalized;
    }
}