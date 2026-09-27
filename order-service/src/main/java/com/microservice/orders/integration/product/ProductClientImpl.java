package com.microservice.orders.integration.product;

import java.util.UUID;

import com.microservice.orders.exception.DownstreamServiceException;
import com.microservice.orders.exception.ProductNotFoundException;
import com.microservice.orders.exception.ProductUnavailableException;
import com.microservice.orders.integration.product.dto.ProductResponse;
import com.microservice.orders.integration.product.dto.ProductSnapshot;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Service
public class ProductClientImpl implements ProductClient {

    private final WebClient productServiceWebClient;

    public ProductClientImpl(
            @Qualifier("productServiceWebClient")
            WebClient productServiceWebClient
    ) {
        this.productServiceWebClient =
                productServiceWebClient;
    }

    @Override
    public ProductResponse getProduct(
            UUID productId
    ) {

        if (productId == null) {
            throw new IllegalArgumentException(
                    "Product ID must not be null"
            );
        }

        try {

            ProductResponse product =
                    productServiceWebClient
                            .get()
                            .uri(
                                    "/api/v1/products/{productId}",
                                    productId
                            )
                            .retrieve()
                            .onStatus(
                                    status ->
                                            status.value() == 404,
                                    response ->
                                            response
                                                    .bodyToMono(
                                                            String.class
                                                    )
                                                    .defaultIfEmpty("")
                                                    .map(body ->
                                                            new ProductNotFoundException(
                                                                    "Product not found: "
                                                                            + productId
                                                            )
                                                    )
                            )
                            .onStatus(
                                    HttpStatusCode::is4xxClientError,
                                    response ->
                                            response
                                                    .bodyToMono(
                                                            String.class
                                                    )
                                                    .defaultIfEmpty("")
                                                    .map(body ->
                                                            new DownstreamServiceException(
                                                                    "Product Service rejected product lookup. HTTP "
                                                                            + response
                                                                                    .statusCode()
                                                                                    .value()
                                                            )
                                                    )
                            )
                            .onStatus(
                                    HttpStatusCode::is5xxServerError,
                                    response ->
                                            response
                                                    .bodyToMono(
                                                            String.class
                                                    )
                                                    .defaultIfEmpty("")
                                                    .map(body ->
                                                            new DownstreamServiceException(
                                                                    "Product Service returned HTTP "
                                                                            + response
                                                                                    .statusCode()
                                                                                    .value()
                                                            )
                                                    )
                            )
                            .bodyToMono(
                                    ProductResponse.class
                            )
                            .block();

            if (product == null) {
                throw new DownstreamServiceException(
                        "Product Service returned an empty response"
                );
            }

            return product;

        } catch (
                ProductNotFoundException
                        | DownstreamServiceException exception
        ) {

            throw exception;

        } catch (Exception exception) {

            throw new DownstreamServiceException(
                    "Unable to communicate with Product Service",
                    exception
            );
        }
    }

    @Override
    public ProductSnapshot getProductSnapshot(
            UUID productId
    ) {

        ProductResponse product =
                getProduct(productId);

        if (product.productId() == null) {
            throw new DownstreamServiceException(
                    "Product Service returned a product without an ID"
            );
        }

        if (product.name() == null
                || product.name().isBlank()) {

            throw new DownstreamServiceException(
                    "Product Service returned a product without a name"
            );
        }

        if (product.price() == null) {

            throw new DownstreamServiceException(
                    "Product Service returned a product without a price"
            );
        }

        if (product.currency() == null
                || product.currency().isBlank()) {

            throw new DownstreamServiceException(
                    "Product Service returned a product without currency"
            );
        }

        if (!"ACTIVE".equals(product.status())) {

            throw new ProductUnavailableException(
                    "Product is not active: " + productId
            );
        }

        if (product.price().signum() <= 0) {

            throw new DownstreamServiceException(
                    "Product Service returned an invalid product price"
            );
        }

        return new ProductSnapshot(
                product.productId(),
                product.name(),
                product.price(),
                product.currency()
        );
    }
}