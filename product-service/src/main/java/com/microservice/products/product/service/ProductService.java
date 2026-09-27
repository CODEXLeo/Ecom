package com.microservice.products.product.service;

import java.util.List;
import java.util.UUID;

import com.microservice.products.product.dto.request.CreateProductRequest;
import com.microservice.products.product.dto.request.UpdateProductRequest;
import com.microservice.products.product.dto.request.UpdateProductStatusRequest;
import com.microservice.products.product.dto.request.UpdateStockRequest;
import com.microservice.products.product.dto.response.ProductResponse;

public interface ProductService {

    List<ProductResponse> getAllProducts();

    ProductResponse getProduct(UUID productId);

    ProductResponse createProduct(CreateProductRequest request);

    ProductResponse updateProduct(
            UUID productId,
            UpdateProductRequest request
    );

    ProductResponse updateStock(
            UUID productId,
            UpdateStockRequest request
    );

    ProductResponse updateStatus(
            UUID productId,
            UpdateProductStatusRequest request
    );

    void deactivateProduct(UUID productId);
}