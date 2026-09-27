package com.microservice.payments.idempotency.service;

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

            return toHex(
                    digest.digest(json)
            );

        } catch (JacksonException exception) {
            throw new IllegalStateException(
                    "Unable to serialize request for idempotency hashing",
                    exception
            );

        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 algorithm is unavailable",
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