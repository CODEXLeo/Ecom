package com.microservice.orders.integration.product;

import java.util.UUID;

import com.microservice.orders.integration.product.config.ProductFeignConfiguration;
import com.microservice.orders.integration.product.dto.ProductResponse;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "product-service", configuration = ProductFeignConfiguration.class)
public interface ProductFeignClient {

    @GetMapping("/api/v1/products/{productId}")
    ProductResponse getProduct(@PathVariable("productId") UUID productId);
}