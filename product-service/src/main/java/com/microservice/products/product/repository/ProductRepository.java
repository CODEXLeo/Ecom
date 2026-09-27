package com.microservice.products.product.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.microservice.products.product.entity.Product;
import com.microservice.products.product.entity.ProductStatus;

import jakarta.persistence.LockModeType;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    List<Product> findByStatusOrderByCreatedAtDesc(ProductStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
           select p
           from Product p
           where p.productId = :productId
           """)
    Optional<Product> findByIdForUpdate(@Param("productId") UUID productId);
}