package com.microservice.orders.config;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import javax.net.ssl.TrustManagerFactory;

import com.microservice.orders.config.properties.MtlsProperties;
import com.microservice.orders.config.properties.PaymentServiceProperties;
import com.microservice.orders.config.properties.ProductServiceProperties;

import io.netty.channel.ChannelOption;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.netty.http.client.HttpClient;

@Configuration
public class WebClientConfig {

    /*
     * ============================================================
     * PRODUCT SERVICE - PUBLIC API
     * ============================================================
     *
     * Used for:
     *
     *     GET /api/v1/products/{productId}
     *
     * Product Service has:
     *
     *     server.ssl.client-auth=want
     *
     * Therefore a client certificate is optional.
     *
     * This client performs TLS server authentication only.
     *
     * IMPORTANT:
     *
     * This WebClient intentionally does NOT configure a
     * KeyManager.
     *
     * Therefore it will NOT present the Order Service client
     * certificate.
     */

    @Bean
    public WebClient productServiceWebClient(
            WebClient.Builder webClientBuilder,
            ProductServiceProperties properties,
            MtlsProperties mtlsProperties
    ) {

        return buildTrustOnlyWebClient(
                webClientBuilder,
                properties.baseUrl(),
                properties.connectTimeout(),
                properties.readTimeout(),
                mtlsProperties
        );
    }


    /*
     * ============================================================
     * PRODUCT SERVICE - INVENTORY
     * ============================================================
     *
     * Used for:
     *
     *     POST /api/v1/inventory/reservations
     *     POST /api/v1/inventory/reservations/{id}/commit
     *     POST /api/v1/inventory/reservations/{id}/release
     *
     * These are internal service-to-service operations.
     *
     * Full mTLS is required.
     *
     * The client certificate/private key is loaded explicitly
     * from the configured PKCS12 keystore.
     */

    @Bean
    public WebClient productInventoryServiceWebClient(
            WebClient.Builder webClientBuilder,
            ProductServiceProperties properties,
            MtlsProperties mtlsProperties
    ) {

        return buildMtlsWebClient(
                webClientBuilder,
                properties.baseUrl(),
                properties.connectTimeout(),
                properties.readTimeout(),
                mtlsProperties
        );
    }


    /*
     * ============================================================
     * PAYMENT SERVICE
     * ============================================================
     *
     * Payment Service communication is service-to-service.
     *
     * Full mTLS is required.
     */

    @Bean
    public WebClient paymentServiceWebClient(
            WebClient.Builder webClientBuilder,
            PaymentServiceProperties properties,
            MtlsProperties mtlsProperties
    ) {

        return buildMtlsWebClient(
                webClientBuilder,
                properties.baseUrl(),
                properties.connectTimeout(),
                properties.readTimeout(),
                mtlsProperties
        );
    }


    /*
     * ============================================================
     * TRUST-ONLY WEBCLIENT
     * ============================================================
     *
     * Used for the public Product Service API.
     *
     * IMPORTANT:
     *
     * No client KeyManager is configured here.
     *
     * Therefore Order Service does NOT present a client
     * certificate for public product GET requests.
     */

    private WebClient buildTrustOnlyWebClient(
            WebClient.Builder builder,
            String baseUrl,
            Duration connectTimeout,
            Duration readTimeout,
            MtlsProperties properties
    ) {

        try {

            TrustManagerFactory trustManagerFactory =
                    createTrustManagerFactory(
                            properties
                    );

            SslContext sslContext =
                    SslContextBuilder
                            .forClient()
                            .protocols("TLSv1.2")
                            .trustManager(
                                    trustManagerFactory
                            )
                            .build();

            HttpClient httpClient =
                    HttpClient.create()
                            .option(
                                    ChannelOption.CONNECT_TIMEOUT_MILLIS,
                                    Math.toIntExact(
                                            connectTimeout.toMillis()
                                    )
                            )
                            .responseTimeout(
                                    readTimeout
                            )
                            .secure(
                                    sslSpec ->
                                            sslSpec.sslContext(
                                                    sslContext
                                            )
                            );

            return builder
                    .clone()
                    .baseUrl(baseUrl)
                    .clientConnector(
                            new ReactorClientHttpConnector(
                                    httpClient
                            )
                    )
                    .build();

        } catch (Exception exception) {

            throw new IllegalStateException(
                    "Unable to initialize Product Service TLS WebClient",
                    exception
            );
        }
    }


    /*
     * ============================================================
     * FULL mTLS WEBCLIENT
     * ============================================================
     *
     * Used for:
     *
     *     Product inventory
     *     Payment Service
     *
     * Configures:
     *
     *     1. explicit client private key
     *     2. explicit client certificate chain
     *     3. downstream certificate trust
     *
     * IMPORTANT:
     *
     * We intentionally DO NOT use:
     *
     *     .keyManager(KeyManagerFactory)
     *
     * here.
     *
     * The previous implementation initialized a generic
     * KeyManagerFactory from the PKCS12 keystore, but Product
     * Service reported:
     *
     *     No client certificate found in request.
     *
     * Therefore the client certificate selection is now made
     * explicit by extracting the PrivateKey and X509Certificate
     * chain from the PKCS12 keystore and passing them directly
     * to Netty.
     */

    private WebClient buildMtlsWebClient(
            WebClient.Builder builder,
            String baseUrl,
            Duration connectTimeout,
            Duration readTimeout,
            MtlsProperties properties
    ) {

        try {

            ClientKeyMaterial clientKeyMaterial =
                    loadClientKeyMaterial(
                            properties
                    );

            TrustManagerFactory trustManagerFactory =
                    createTrustManagerFactory(
                            properties
                    );

            /*
             * ----------------------------------------------------
             * NETTY SSL CONTEXT
             * ----------------------------------------------------
             *
             * Explicitly provide:
             *
             *     PrivateKey
             *     X509Certificate[]
             *
             * This removes any ambiguity around KeyManagerFactory
             * certificate selection.
             */

            SslContext sslContext =
                    SslContextBuilder
                            .forClient()
                            .protocols("TLSv1.2")
                            .keyManager(
                                    clientKeyMaterial.privateKey(),
                                    clientKeyMaterial.certificateChain()
                            )
                            .trustManager(
                                    trustManagerFactory
                            )
                            .build();

            /*
             * ----------------------------------------------------
             * REACTOR NETTY HTTP CLIENT
             * ----------------------------------------------------
             */

            HttpClient httpClient =
                    HttpClient.create()
                            .option(
                                    ChannelOption.CONNECT_TIMEOUT_MILLIS,
                                    Math.toIntExact(
                                            connectTimeout.toMillis()
                                    )
                            )
                            .responseTimeout(
                                    readTimeout
                            )
                            .secure(
                                    sslSpec ->
                                            sslSpec.sslContext(
                                                    sslContext
                                            )
                            );

            /*
             * ----------------------------------------------------
             * WEBCLIENT
             * ----------------------------------------------------
             */

            return builder
                    .clone()
                    .baseUrl(baseUrl)
                    .clientConnector(
                            new ReactorClientHttpConnector(
                                    httpClient
                            )
                    )
                    .build();

        } catch (Exception exception) {

            throw new IllegalStateException(
                    "Unable to initialize mTLS WebClient",
                    exception
            );
        }
    }


    /*
     * ============================================================
     * CLIENT KEY MATERIAL
     * ============================================================
     *
     * Loads the client private key and certificate chain directly
     * from:
     *
     *     service.mtls.client-key-store-path
     *
     * which currently points to:
     *
     *     order-client.p12
     *
     * The keystore has already been verified to contain:
     *
     *     PrivateKeyEntry
     *     clientAuth certificate
     *     CN=order-service
     */

    private ClientKeyMaterial loadClientKeyMaterial(
            MtlsProperties properties
    ) throws Exception {

        KeyStore keyStore =
                KeyStore.getInstance("PKCS12");

        Path keyStorePath =
                Path.of(
                        properties.clientKeyStorePath()
                );

        char[] password =
                properties
                        .clientKeyStorePassword()
                        .toCharArray();

        try (
                InputStream inputStream =
                        Files.newInputStream(
                                keyStorePath
                        )
        ) {

            keyStore.load(
                    inputStream,
                    password
            );
        }

        /*
         * --------------------------------------------------------
         * FIND PRIVATE KEY ENTRY
         * --------------------------------------------------------
         */

        String keyAlias = null;

        Enumeration<String> aliases =
                keyStore.aliases();

        while (aliases.hasMoreElements()) {

            String alias =
                    aliases.nextElement();

            if (keyStore.isKeyEntry(alias)) {

                keyAlias = alias;
                break;
            }
        }

        if (keyAlias == null) {

            throw new IllegalStateException(
                    "No private-key entry found in client keystore: "
                            + keyStorePath
            );
        }

        /*
         * --------------------------------------------------------
         * LOAD PRIVATE KEY
         * --------------------------------------------------------
         */

        java.security.Key key =
                keyStore.getKey(
                        keyAlias,
                        password
                );

        if (!(key instanceof PrivateKey privateKey)) {

            throw new IllegalStateException(
                    "Keystore entry is not a PrivateKey: "
                            + keyAlias
            );
        }

        /*
         * --------------------------------------------------------
         * LOAD CERTIFICATE CHAIN
         * --------------------------------------------------------
         */

        Certificate[] certificateChain =
                keyStore.getCertificateChain(
                        keyAlias
                );

        if (certificateChain == null
                || certificateChain.length == 0) {

            throw new IllegalStateException(
                    "No certificate chain found for private-key alias: "
                            + keyAlias
            );
        }

        List<X509Certificate> x509Certificates =
                new ArrayList<>(
                        certificateChain.length
                );

        for (Certificate certificate : certificateChain) {

            if (!(certificate instanceof X509Certificate x509Certificate)) {

                throw new IllegalStateException(
                        "Client certificate is not X.509: "
                                + certificate.getType()
                );
            }

            x509Certificates.add(
                    x509Certificate
            );
        }

        X509Certificate[] certificateChainArray =
                x509Certificates.toArray(
                        new X509Certificate[0]
                );

        return new ClientKeyMaterial(
                privateKey,
                certificateChainArray
        );
    }


    /*
     * ============================================================
     * TRUSTSTORE
     * ============================================================
     *
     * Loads the certificates that Order Service trusts when
     * connecting to downstream HTTPS services.
     */

    private TrustManagerFactory createTrustManagerFactory(
            MtlsProperties properties
    ) throws Exception {

        KeyStore trustStore =
                KeyStore.getInstance("PKCS12");

        Path trustStorePath =
                Path.of(
                        properties.trustStorePath()
                );

        try (
                InputStream inputStream =
                        Files.newInputStream(
                                trustStorePath
                        )
        ) {

            trustStore.load(
                    inputStream,
                    properties
                            .trustStorePassword()
                            .toCharArray()
            );
        }

        TrustManagerFactory trustManagerFactory =
                TrustManagerFactory.getInstance(
                        TrustManagerFactory
                                .getDefaultAlgorithm()
                );

        trustManagerFactory.init(
                trustStore
        );

        return trustManagerFactory;
    }


    /*
     * ============================================================
     * CLIENT KEY MATERIAL RECORD
     * ============================================================
     */

    private record ClientKeyMaterial(
            PrivateKey privateKey,
            X509Certificate[] certificateChain
    ) {
    }
}