package com.microservice.orders.integration.product;

import java.util.UUID;

import com.microservice.orders.integration.product.dto.ProductResponse;
import com.microservice.orders.integration.product.dto.ProductSnapshot;

public interface ProductClient {

    ProductResponse getProduct(UUID productId);

    ProductSnapshot getProductSnapshot(UUID productId);
}