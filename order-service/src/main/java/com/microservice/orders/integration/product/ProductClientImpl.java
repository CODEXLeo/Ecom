package com.microservice.orders.integration.product;

import java.util.UUID;

import com.microservice.orders.exception.DownstreamServiceException;
import com.microservice.orders.exception.ProductNotFoundException;
import com.microservice.orders.exception.ProductUnavailableException;
import com.microservice.orders.integration.product.dto.ProductResponse;
import com.microservice.orders.integration.product.dto.ProductSnapshot;

import feign.FeignException;

import org.springframework.stereotype.Service;

@Service
public class ProductClientImpl implements ProductClient {

    private final ProductFeignClient productFeignClient;

    public ProductClientImpl(
            ProductFeignClient productFeignClient
    ) {
        this.productFeignClient =
                productFeignClient;
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
                    productFeignClient.getProduct(
                            productId
                    );

            if (product == null) {
                throw new DownstreamServiceException(
                        "Product Service returned an empty response"
                );
            }

            return product;

        } catch (FeignException.NotFound exception) {

            throw new ProductNotFoundException(
                    "Product not found: " + productId
            );

        } catch (
                DownstreamServiceException
                        | ProductNotFoundException exception
        ) {

            throw exception;

        } catch (FeignException exception) {

            throw new DownstreamServiceException(
                    "Product Service rejected product lookup. HTTP "
                            + exception.status(),
                    exception
            );

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