package com.microservice.gateway.security;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import org.springframework.stereotype.Component;

@Component
public class PemKeyLoader {

    public RSAPublicKey loadPublicKey(String path) {

        try {

            String pem =
                    Files.readString(
                            Path.of(path)
                    );

            String base64 =
                    pem
                            .replace(
                                    "-----BEGIN PUBLIC KEY-----",
                                    ""
                            )
                            .replace(
                                    "-----END PUBLIC KEY-----",
                                    ""
                            )
                            .replaceAll(
                                    "\\s",
                                    ""
                            );

            byte[] keyBytes =
                    Base64.getDecoder()
                            .decode(base64);

            X509EncodedKeySpec keySpec =
                    new X509EncodedKeySpec(
                            keyBytes
                    );

            KeyFactory keyFactory =
                    KeyFactory.getInstance(
                            "RSA"
                    );

            return (RSAPublicKey)
                    keyFactory.generatePublic(
                            keySpec
                    );

        } catch (IOException exception) {

            throw new IllegalStateException(
                    "Unable to read JWT public key: "
                            + path,
                    exception
            );

        } catch (NoSuchAlgorithmException exception) {

            throw new IllegalStateException(
                    "RSA algorithm is unavailable",
                    exception
            );

        } catch (InvalidKeySpecException exception) {

            throw new IllegalStateException(
                    "Invalid RSA public key: "
                            + path,
                    exception
            );
        }
    }
}