package com.microservice.orders.integration.payment.config;

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
import com.microservice.orders.config.properties.PaymentServiceProperties;

import feign.Client;
import feign.Logger;
import feign.Request;

import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;
import org.springframework.cloud.openfeign.loadbalancer.FeignBlockingLoadBalancerClient;
import org.springframework.cloud.openfeign.loadbalancer.LoadBalancerFeignRequestTransformer;
import org.springframework.context.annotation.Bean;

/**
 * Feign configuration for Payment Service.
 *
 * <p>
 * Payment Service communication is service-to-service and
 * requires mutual TLS.
 *
 * <p>
 * This configuration loads:
 *
 * <ul>
 *     <li>Order Service client private key</li>
 *     <li>Order Service client certificate chain</li>
 *     <li>Payment Service truststore</li>
 * </ul>
 *
 * <p>
 * This class is intentionally not annotated with
 * {@code @Configuration}. It is used only by
 * {@code PaymentFeignClient}.
 */
public class PaymentFeignConfiguration {

    private static final Duration DEFAULT_CONNECT_TIMEOUT =
            Duration.ofSeconds(5);

    private static final Duration DEFAULT_READ_TIMEOUT =
            Duration.ofSeconds(15);

    /**
     * Creates the Feign HTTP client using full mTLS and
     * Eureka-based client-side load balancing.
     *
     * <p>
     * The {@link Client.Default} instance is responsible for
     * the actual HTTPS/mTLS connection.
     *
     * <p>
     * The {@link FeignBlockingLoadBalancerClient} resolves
     * {@code payment-service} through Spring Cloud LoadBalancer
     * before delegating the actual request to the mTLS client.
     */
    @Bean
    public Client paymentFeignClient(
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
     * Payment Service.
     *
     * <p>
     * Profile-specific configuration values override
     * the defaults when they are available.
     */
    @Bean
    public Request.Options paymentFeignRequestOptions(
            PaymentServiceProperties properties
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
                toIntMillis(connectTimeout);

        int readTimeoutMillis =
                toIntMillis(readTimeout);

        return new Request.Options(
                connectTimeoutMillis,
                readTimeoutMillis
        );
    }

    /**
     * Enables detailed Feign logging while debugging.
     */
    @Bean
    public Logger.Level feignLoggerLevel() {
        return Logger.Level.FULL;
    }

    /**
     * Creates the SSLContext used by the Payment Feign client.
     *
     * <p>
     * The SSL context contains:
     *
     * <ul>
     *     <li>KeyManager for Order Service identity</li>
     *     <li>TrustManager for Payment Service certificate validation</li>
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
                    "Unable to initialize Payment Service "
                            + "Feign mTLS client",
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
                KeyStore.getInstance("PKCS12");

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
     * Payment Service's TLS certificate.
     */
    private KeyStore loadTrustStore(
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
                    properties.trustStorePassword()
                            .toCharArray()
            );
        }

        return trustStore;
    }

    /**
     * Converts a Duration into the integer millisecond
     * value required by Feign Request.Options.
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