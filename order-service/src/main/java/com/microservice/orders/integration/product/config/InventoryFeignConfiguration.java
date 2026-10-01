package com.microservice.orders.integration.product.config;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Duration;
import java.util.List;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import com.microservice.orders.config.properties.MtlsProperties;
import com.microservice.orders.config.properties.ProductServiceProperties;

import feign.Client;
import feign.Request;

import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;
import org.springframework.cloud.openfeign.loadbalancer.FeignBlockingLoadBalancerClient;
import org.springframework.cloud.openfeign.loadbalancer.LoadBalancerFeignRequestTransformer;
import org.springframework.context.annotation.Bean;

/**
 * Feign configuration for Product Service inventory operations.
 *
 * <p>
 * Inventory operations require mutual TLS.
 *
 * <p>
 * This configuration therefore creates an SSLContext
 * containing both:
 *
 * <ul>
 *     <li>the Order Service client identity</li>
 *     <li>the Product Service trust material</li>
 * </ul>
 *
 * <p>
 * The Feign client is wrapped with Spring Cloud LoadBalancer
 * so that the logical service name {@code product-service}
 * is resolved through Eureka instead of being treated as
 * a DNS hostname.
 */
public class InventoryFeignConfiguration {

    private static final Duration DEFAULT_CONNECT_TIMEOUT =
            Duration.ofSeconds(5);

    private static final Duration DEFAULT_READ_TIMEOUT =
            Duration.ofSeconds(10);

    /**
     * Creates the Feign HTTP client using full mTLS.
     *
     * <p>
     * The inner {@link Client.Default} performs the actual
     * HTTPS connection using the custom mTLS SSLContext.
     *
     * <p>
     * The {@link FeignBlockingLoadBalancerClient} resolves
     * {@code product-service} through Spring Cloud
     * LoadBalancer/Eureka before delegating the request to
     * the HTTPS client.
     */
    @Bean
    public Client inventoryFeignClient(
            MtlsProperties mtlsProperties,
            LoadBalancerClient loadBalancerClient,
            LoadBalancerClientFactory loadBalancerClientFactory,
            List<LoadBalancerFeignRequestTransformer> transformers
    ) {

        SSLContext sslContext =
                createMtlsSslContext(
                        mtlsProperties
                );

        Client delegate =
                new Client.Default(
                        sslContext.getSocketFactory(),
                        HttpsURLConnection
                                .getDefaultHostnameVerifier()
                );

        return new FeignBlockingLoadBalancerClient(
                delegate,
                loadBalancerClient,
                loadBalancerClientFactory,
                transformers
        );
    }

    /**
     * Configures Feign connect/read timeouts for
     * Product Service inventory operations.
     */
    @Bean
    public Request.Options inventoryFeignRequestOptions(
            ProductServiceProperties properties
    ) {

        Duration connectTimeout =
                properties.connectTimeout() != null
                        ? properties.connectTimeout()
                        : DEFAULT_CONNECT_TIMEOUT;

        Duration readTimeout =
                properties.readTimeout() != null
                        ? properties.readTimeout()
                        : DEFAULT_READ_TIMEOUT;

        int connectTimeoutMillis =
                toIntMillis(
                        connectTimeout
                );

        int readTimeoutMillis =
                toIntMillis(
                        readTimeout
                );

        return new Request.Options(
                connectTimeoutMillis,
                readTimeoutMillis
        );
    }

    /**
     * Creates the SSL context used for Product Service
     * inventory operations.
     *
     * <p>
     * This SSL context contains BOTH:
     *
     * <ul>
     *     <li>a KeyManager for the Order Service client identity</li>
     *     <li>a TrustManager for Product Service certificate validation</li>
     * </ul>
     */
    private SSLContext createMtlsSslContext(
            MtlsProperties properties
    ) {

        try {

            KeyStore clientKeyStore =
                    loadClientKeyStore(
                            properties
                    );

            KeyManagerFactory keyManagerFactory =
                    KeyManagerFactory.getInstance(
                            KeyManagerFactory
                                    .getDefaultAlgorithm()
                    );

            keyManagerFactory.init(
                    clientKeyStore,
                    properties.clientKeyStorePassword()
                            .toCharArray()
            );

            KeyStore trustStore =
                    loadTrustStore(
                            properties
                    );

            TrustManagerFactory trustManagerFactory =
                    TrustManagerFactory.getInstance(
                            TrustManagerFactory
                                    .getDefaultAlgorithm()
                    );

            trustManagerFactory.init(
                    trustStore
            );

            SSLContext sslContext =
                    SSLContext.getInstance(
                            "TLSv1.2"
                    );

            sslContext.init(
                    keyManagerFactory.getKeyManagers(),
                    trustManagerFactory.getTrustManagers(),
                    null
            );

            return sslContext;

        } catch (Exception exception) {

            throw new IllegalStateException(
                    "Unable to initialize Product Service "
                            + "Inventory Feign mTLS client",
                    exception
            );
        }
    }

    /**
     * Loads the PKCS12 keystore containing the
     * Order Service private key and certificate chain.
     */
    private KeyStore loadClientKeyStore(
            MtlsProperties properties
    ) throws Exception {

        KeyStore clientKeyStore =
                KeyStore.getInstance(
                        "PKCS12"
                );

        Path clientKeyStorePath =
                Path.of(
                        properties.clientKeyStorePath()
                );

        try (
                InputStream inputStream =
                        Files.newInputStream(
                                clientKeyStorePath
                        )
        ) {

            clientKeyStore.load(
                    inputStream,
                    properties.clientKeyStorePassword()
                            .toCharArray()
            );
        }

        return clientKeyStore;
    }

    /**
     * Loads the PKCS12 truststore used to validate
     * Product Service's TLS certificate.
     */
    private KeyStore loadTrustStore(
            MtlsProperties properties
    ) throws Exception {

        KeyStore trustStore =
                KeyStore.getInstance(
                        "PKCS12"
                );

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
                    properties.trustStorePassword()
                            .toCharArray()
            );
        }

        return trustStore;
    }

    /**
     * Converts a Duration into the integer millisecond
     * value required by Feign.
     */
    private int toIntMillis(
            Duration duration
    ) {

        long milliseconds =
                duration.toMillis();

        if (milliseconds < 0) {

            throw new IllegalArgumentException(
                    "Feign timeout must not be negative"
            );
        }

        if (milliseconds > Integer.MAX_VALUE) {

            throw new IllegalArgumentException(
                    "Feign timeout is too large: "
                            + milliseconds
                            + " ms"
            );
        }

        return (int) milliseconds;
    }
}