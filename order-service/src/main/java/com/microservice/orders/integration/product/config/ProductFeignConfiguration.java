package com.microservice.orders.integration.product.config;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Duration;
import java.util.List;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import com.microservice.orders.config.properties.MtlsProperties;
import com.microservice.orders.config.properties.ProductServiceProperties;

import feign.Client;
import feign.Logger;
import feign.Request;

import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;
import org.springframework.cloud.openfeign.loadbalancer.FeignBlockingLoadBalancerClient;
import org.springframework.cloud.openfeign.loadbalancer.LoadBalancerFeignRequestTransformer;
import org.springframework.context.annotation.Bean;

/**
 * Feign configuration for the public Product Service API.
 *
 * <p>
 * Product catalogue endpoints use server-side TLS only.
 * The Order Service client certificate is intentionally
 * not presented for these requests.
 *
 * <p>
 * The Feign client is wrapped with Spring Cloud LoadBalancer
 * so that the logical service name {@code product-service}
 * is resolved through Eureka instead of being treated as
 * a DNS hostname.
 */
public class ProductFeignConfiguration {

    private static final Duration DEFAULT_CONNECT_TIMEOUT =
            Duration.ofSeconds(5);

    private static final Duration DEFAULT_READ_TIMEOUT =
            Duration.ofSeconds(10);

    /**
     * Creates the HTTP client used by ProductFeignClient.
     *
     * <p>
     * The inner {@link Client.Default} is responsible for the
     * actual HTTPS connection and uses the custom trust-only
     * SSLContext.
     *
     * <p>
     * The {@link FeignBlockingLoadBalancerClient} wraps that
     * client and resolves {@code product-service} through
     * Spring Cloud LoadBalancer/Eureka.
     *
     * <p>
     * No client certificate is configured here because the
     * Product catalogue API is a public server-TLS endpoint.
     */
    @Bean
    public Client productFeignClient(
            MtlsProperties mtlsProperties,
            LoadBalancerClient loadBalancerClient,
            LoadBalancerClientFactory loadBalancerClientFactory,
            List<LoadBalancerFeignRequestTransformer> transformers
    ) {
        SSLContext sslContext =
                createTrustOnlySslContext(
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
     * Configures Feign connect/read timeouts for Product Service.
     */
    @Bean
    public Request.Options productFeignRequestOptions(
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
                toIntMillis(connectTimeout);

        int readTimeoutMillis =
                toIntMillis(readTimeout);

        return new Request.Options(
                connectTimeoutMillis,
                readTimeoutMillis
        );
    }

    /**
     * Creates an SSLContext that trusts certificates from
     * the configured Product/Order truststore.
     *
     * <p>
     * There is intentionally NO KeyManagerFactory here.
     * Therefore this client cannot present the Order Service
     * client certificate during the TLS handshake.
     */
    private SSLContext createTrustOnlySslContext(
            MtlsProperties properties
    ) {
        try {
            KeyStore trustStore =
                    KeyStore.getInstance("PKCS12");

            Path trustStorePath =
                    Path.of(properties.trustStorePath());

            try (InputStream inputStream =
                         Files.newInputStream(trustStorePath)) {

                trustStore.load(
                        inputStream,
                        properties.trustStorePassword()
                                .toCharArray()
                );
            }

            TrustManagerFactory trustManagerFactory =
                    TrustManagerFactory.getInstance(
                            TrustManagerFactory.getDefaultAlgorithm()
                    );

            trustManagerFactory.init(trustStore);

            SSLContext sslContext =
                    SSLContext.getInstance("TLSv1.2");

            sslContext.init(
                    null,
                    trustManagerFactory.getTrustManagers(),
                    null
            );

            return sslContext;

        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Unable to initialize Product Service Feign TLS client",
                    exception
            );
        }
    }

    /**
     * Converts a Duration to the integer millisecond value
     * required by Feign.
     */
    private int toIntMillis(Duration duration) {
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

    /**
     * Enables full Feign request/response logging.
     */
    @Bean
    public Logger.Level feignLoggerLevel() {
        return Logger.Level.FULL;
    }
}