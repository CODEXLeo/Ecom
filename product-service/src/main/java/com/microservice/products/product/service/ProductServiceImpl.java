package com.microservice.products.product.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.microservice.products.exception.ProductNotFoundException;
import com.microservice.products.exception.ProductValidationException;
import com.microservice.products.product.dto.request.CreateProductRequest;
import com.microservice.products.product.dto.request.UpdateProductRequest;
import com.microservice.products.product.dto.request.UpdateProductStatusRequest;
import com.microservice.products.product.dto.request.UpdateStockRequest;
import com.microservice.products.product.dto.response.ProductResponse;
import com.microservice.products.product.entity.Product;
import com.microservice.products.product.entity.ProductStatus;
import com.microservice.products.product.repository.ProductRepository;

@Service
@Transactional(readOnly = true)
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;

    public ProductServiceImpl(
            ProductRepository productRepository) {

        this.productRepository = productRepository;
    }

    /*
     * ============================================================
     * PUBLIC CATALOGUE
     * ============================================================
     */

    @Override
    public List<ProductResponse> getAllProducts() {

        return productRepository
                .findByStatusOrderByCreatedAtDesc(
                        ProductStatus.ACTIVE
                )
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public ProductResponse getProduct(
            UUID productId) {

        Product product =
                productRepository
                        .findById(productId)
                        .filter(existing ->
                                existing.getStatus()
                                        == ProductStatus.ACTIVE
                        )
                        .orElseThrow(() ->
                                new ProductNotFoundException(
                                        productId
                                )
                        );

        return toResponse(product);
    }

    /*
     * ============================================================
     * CREATE
     * ============================================================
     */

    @Override
    @Transactional
    public ProductResponse createProduct(
            CreateProductRequest request) {

        Product product =
                new Product(
                        normalizeName(request.name()),
                        normalizeDescription(
                                request.description()
                        ),
                        normalizePrice(request.price()),
                        normalizeCurrency(request.currency()),
                        normalizeStockQuantity(
                                request.stockQuantity()
                        )
                );

        Product savedProduct =
                productRepository.save(product);

        return toResponse(savedProduct);
    }

    /*
     * ============================================================
     * UPDATE DETAILS
     * ============================================================
     */

    @Override
    @Transactional
    public ProductResponse updateProduct(
            UUID productId,
            UpdateProductRequest request) {

        Product product =
                findProduct(productId);

        if (request.name() == null
                && request.description() == null
                && request.price() == null
                && request.currency() == null) {

            throw new ProductValidationException(
                    "At least one product field must be provided"
            );
        }

        String name =
                request.name() != null
                        ? normalizeName(request.name())
                        : product.getName();

        String description =
                request.description() != null
                        ? normalizeDescription(
                                request.description()
                        )
                        : product.getDescription();

        BigDecimal price =
                request.price() != null
                        ? normalizePrice(request.price())
                        : product.getPrice();

        String currency =
                request.currency() != null
                        ? normalizeCurrency(
                                request.currency()
                        )
                        : product.getCurrency();

        product.updateDetails(
                name,
                description,
                price,
                currency
        );

        return toResponse(product);
    }

    /*
     * ============================================================
     * ADMIN STOCK UPDATE
     * ============================================================
     *
     * This is an absolute stock adjustment.
     *
     * It is NOT the mechanism used by checkout.
     *
     * Checkout uses InventoryService.reserve().
     */

    @Override
    @Transactional
    public ProductResponse updateStock(
            UUID productId,
            UpdateStockRequest request) {

        /*
         * IMPORTANT:
         *
         * Reservations also lock this row using
         * findByIdForUpdate().
         *
         * Therefore admin stock changes and reservations
         * serialize correctly.
         */
        Product product =
                productRepository
                        .findByIdForUpdate(productId)
                        .orElseThrow(() ->
                                new ProductNotFoundException(
                                        productId
                                )
                        );

        product.updateStock(
                normalizeStockQuantity(
                        request.stockQuantity()
                )
        );

        return toResponse(product);
    }

    /*
     * ============================================================
     * STATUS
     * ============================================================
     */

    @Override
    @Transactional
    public ProductResponse updateStatus(
            UUID productId,
            UpdateProductStatusRequest request) {

        Product product =
                findProduct(productId);

        product.updateStatus(
                request.status()
        );

        return toResponse(product);
    }

    /*
     * ============================================================
     * SOFT DELETE
     * ============================================================
     */

    @Override
    @Transactional
    public void deactivateProduct(
            UUID productId) {

        Product product =
                findProduct(productId);

        product.updateStatus(
                ProductStatus.INACTIVE
        );
    }

    /*
     * ============================================================
     * LOOKUP
     * ============================================================
     */

    private Product findProduct(
            UUID productId) {

        return productRepository
                .findById(productId)
                .orElseThrow(() ->
                        new ProductNotFoundException(
                                productId
                        )
                );
    }

    /*
     * ============================================================
     * RESPONSE
     * ============================================================
     */

    private ProductResponse toResponse(
            Product product) {

        return new ProductResponse(
                product.getProductId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getCurrency(),
                product.getStockQuantity(),
                product.getStatus(),
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }

    /*
     * ============================================================
     * NORMALIZATION
     * ============================================================
     */

    private String normalizeName(
            String name) {

        String normalized =
                name.trim();

        if (normalized.isEmpty()) {

            throw new ProductValidationException(
                    "Product name cannot be blank"
            );
        }

        return normalized;
    }

    private String normalizeDescription(
            String description) {

        if (description == null) {
            return null;
        }

        String normalized =
                description.trim();

        return normalized.isEmpty()
                ? null
                : normalized;
    }

    private BigDecimal normalizePrice(
            BigDecimal price) {

        if (price == null
                || price.signum() <= 0) {

            throw new ProductValidationException(
                    "Product price must be greater than zero"
            );
        }

        if (price.scale() > 2) {

            throw new ProductValidationException(
                    "Product price must have at most 2 decimal places"
            );
        }

        return price.setScale(2);
    }

    private String normalizeCurrency(
            String currency) {

        if (currency == null) {

            throw new ProductValidationException(
                    "Currency is required"
            );
        }

        String normalized =
                currency
                        .trim()
                        .toUpperCase(Locale.ROOT);

        if (!normalized.matches("[A-Z]{3}")) {

            throw new ProductValidationException(
                    "Currency must be a 3-letter uppercase ISO code"
            );
        }

        return normalized;
    }

    private Integer normalizeStockQuantity(
            Integer stockQuantity) {

        if (stockQuantity == null
                || stockQuantity < 0) {

            throw new ProductValidationException(
                    "Stock quantity cannot be negative"
            );
        }

        return stockQuantity;
    }
}