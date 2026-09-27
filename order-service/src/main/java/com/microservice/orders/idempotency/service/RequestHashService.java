package com.microservice.orders.idempotency.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Service;

@Service
public class RequestHashService {

    private final ObjectMapper objectMapper;

    public RequestHashService(
            ObjectMapper objectMapper
    ) {
        this.objectMapper =
                objectMapper;
    }

    /**
     * Creates a SHA-256 hash of the JSON representation of the request.
     *
     * The idempotency key itself is intentionally NOT part of this hash.
     *
     * The hash represents the logical request parameters. Therefore:
     *
     * same key + same request
     *     -> same hash
     *
     * same key + different request
     *     -> different hash
     */
    public String hash(
            Object request
    ) {

        if (request == null) {
            throw new IllegalArgumentException(
                    "Request must not be null"
            );
        }

        try {
            byte[] json =
                    objectMapper.writeValueAsBytes(
                            request
                    );

            MessageDigest digest =
                    MessageDigest.getInstance(
                            "SHA-256"
                    );

            byte[] hash =
                    digest.digest(json);

            return toHex(hash);

        } catch (JacksonException exception) {

            throw new IllegalStateException(
                    "Unable to serialize request for idempotency hashing",
                    exception
            );

        } catch (NoSuchAlgorithmException exception) {

            throw new IllegalStateException(
                    "SHA-256 algorithm is not available",
                    exception
            );
        }
    }

    private static String toHex(
            byte[] bytes
    ) {

        StringBuilder result =
                new StringBuilder(
                        bytes.length * 2
                );

        for (byte value : bytes) {

            result.append(
                    String.format(
                            "%02x",
                            value & 0xff
                    )
            );
        }

        return result.toString();
    }
}