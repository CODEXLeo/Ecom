package com.microservice.orders.integration.product.config;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Duration;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import com.microservice.orders.config.properties.MtlsProperties;
import com.microservice.orders.config.properties.ProductServiceProperties;

import feign.Client;
import feign.Request;

import org.springframework.context.annotation.Bean;

/**
 * Feign configuration for the public Product Service API.
 *
 * <p>
 * IMPORTANT:
 *
 * <ul>
 *     <li>Uses HTTPS server authentication.</li>
 *     <li>Uses the configured truststore.</li>
 *     <li>Does NOT load the Order Service client certificate.</li>
 *     <li>Does NOT configure a KeyManager.</li>
 *     <li>Preserves normal hostname verification.</li>
 *     <li>Uses the Product Service connect/read timeout settings.</li>
 * </ul>
 *
 * <p>
 * This configuration is intentionally NOT annotated with
 * {@code @Configuration}.
 *
 * It is imported only by ProductFeignClient through:
 *
 * {@code @FeignClient(configuration = ProductFeignConfiguration.class)}
 *
 * This prevents this configuration from becoming a global Feign
 * configuration for every Feign client.
 */
public class ProductFeignConfiguration {

    /**
     * Creates the HTTP client used by ProductFeignClient.
     *
     * <p>
     * Spring Cloud LoadBalancer can wrap this Feign Client and select
     * the Product Service instance discovered through Eureka.
     */
    @Bean
    public Client productFeignClient(
            MtlsProperties mtlsProperties
    ) {
        SSLContext sslContext =
                createTrustOnlySslContext(
                        mtlsProperties
                );

        HostnameVerifier hostnameVerifier =
                HttpsURLConnection
                        .getDefaultHostnameVerifier();

        return new Client.Default(
                sslContext.getSocketFactory(),
                hostnameVerifier
        );
    }

    /**
     * Configures Feign connect/read timeouts for Product Service.
     */
    @Bean
    public Request.Options productFeignRequestOptions(
            ProductServiceProperties properties
    ) {
        int connectTimeoutMillis =
                toIntMillis(
                        properties.connectTimeout()
                );

        int readTimeoutMillis =
                toIntMillis(
                        properties.readTimeout()
                );

        return new Request.Options(
                connectTimeoutMillis,
                readTimeoutMillis
        );
    }

    /**
     * Creates an SSLContext that trusts certificates from the
     * configured Product/Order truststore.
     *
     * <p>
     * There is intentionally NO KeyManagerFactory here.
     *
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
                    Path.of(
                            properties.trustStorePath()
                    );

            try (InputStream inputStream =
                         Files.newInputStream(
                                 trustStorePath
                         )) {

                trustStore.load(
                        inputStream,
                        properties.trustStorePassword()
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

            SSLContext sslContext =
                    SSLContext.getInstance("TLSv1.2");

            sslContext.init(
                    null,
                    trustManagerFactory
                            .getTrustManagers(),
                    null
            );

            return sslContext;

        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Unable to initialize Product Service "
                            + "Feign TLS client",
                    exception
            );
        }
    }

    /**
     * Converts a Duration into the integer millisecond value
     * required by Feign Request.Options.
     */
    private int toIntMillis(
            Duration duration
    ) {
        long milliseconds =
                duration.toMillis();

        if (milliseconds < 0) {
            throw new IllegalArgumentException("Feign timeout must not be negative");
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