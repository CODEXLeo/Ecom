package com.microservice.products.product.controller;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.microservice.products.product.dto.request.CreateProductRequest;
import com.microservice.products.product.dto.request.UpdateProductRequest;
import com.microservice.products.product.dto.request.UpdateProductStatusRequest;
import com.microservice.products.product.dto.request.UpdateStockRequest;
import com.microservice.products.product.dto.response.ProductResponse;
import com.microservice.products.product.service.ProductService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/products")
@Tag(
        name = "Products",
        description = "Product catalogue and product administration APIs"
)
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    /*
     * ============================================================
     * PUBLIC CATALOGUE
     * ============================================================
     */

    @GetMapping
    @Operation(
            summary = "Get active products",
            description = "Returns all currently active products."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Products retrieved successfully"
            )
    })
    public ResponseEntity<List<ProductResponse>> getAllProducts() {

        return ResponseEntity.ok(
                productService.getAllProducts()
        );
    }

    @GetMapping("/{productId}")
    @Operation(
            summary = "Get product",
            description = "Returns an active product by UUID."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Product found"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Product not found"
            )
    })
    public ResponseEntity<ProductResponse> getProduct(

            @Parameter(
                    description = "Product UUID",
                    required = true
            )
            @PathVariable UUID productId) {

        return ResponseEntity.ok(
                productService.getProduct(productId)
        );
    }

    /*
     * ============================================================
     * ADMIN: CREATE
     * ============================================================
     */

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Create product",
            description = "Creates a new active product. Requires ADMIN role.",
            security = {
                    @SecurityRequirement(name = "bearerAuth")
            }
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Product created"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid product data"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Authentication required"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "ADMIN role required"
            )
    })
    public ResponseEntity<ProductResponse> createProduct(
            @Valid @RequestBody CreateProductRequest request) {

        ProductResponse response =
                productService.createProduct(request);

        URI location = URI.create(
                "/api/v1/products/" + response.productId()
        );

        return ResponseEntity
                .created(location)
                .body(response);
    }

    /*
     * ============================================================
     * ADMIN: UPDATE DETAILS
     * ============================================================
     */

    @PatchMapping("/{productId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Update product details",
            description = "Partially updates product details. Requires ADMIN role.",
            security = {
                    @SecurityRequirement(name = "bearerAuth")
            }
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Product updated"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid product data"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Authentication required"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "ADMIN role required"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Product not found"
            )
    })
    public ResponseEntity<ProductResponse> updateProduct(

            @Parameter(
                    description = "Product UUID",
                    required = true
            )
            @PathVariable UUID productId,

            @Valid @RequestBody UpdateProductRequest request) {

        return ResponseEntity.ok(
                productService.updateProduct(
                        productId,
                        request
                )
        );
    }

    /*
     * ============================================================
     * ADMIN: STOCK
     * ============================================================
     */

    @PatchMapping("/{productId}/stock")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Update product stock",
            description = "Sets the absolute stock quantity. Requires ADMIN role.",
            security = {
                    @SecurityRequirement(name = "bearerAuth")
            }
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Stock updated"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid stock quantity"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Authentication required"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "ADMIN role required"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Product not found"
            )
    })
    public ResponseEntity<ProductResponse> updateStock(

            @Parameter(
                    description = "Product UUID",
                    required = true
            )
            @PathVariable UUID productId,

            @Valid @RequestBody UpdateStockRequest request) {

        return ResponseEntity.ok(
                productService.updateStock(
                        productId,
                        request
                )
        );
    }

    /*
     * ============================================================
     * ADMIN: STATUS
     * ============================================================
     */

    @PatchMapping("/{productId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Change product status",
            description = "Activates or deactivates a product.",
            security = {
                    @SecurityRequirement(name = "bearerAuth")
            }
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Product status updated"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid product status"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Authentication required"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "ADMIN role required"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Product not found"
            )
    })
    public ResponseEntity<ProductResponse> updateStatus(

            @Parameter(
                    description = "Product UUID",
                    required = true
            )
            @PathVariable UUID productId,

            @Valid @RequestBody UpdateProductStatusRequest request) {

        return ResponseEntity.ok(
                productService.updateStatus(
                        productId,
                        request
                )
        );
    }

    /*
     * ============================================================
     * ADMIN: SOFT DELETE
     * ============================================================
     */

    @DeleteMapping("/{productId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Deactivate product",
            description =
                    "Soft-deletes a product by changing its status to INACTIVE.",
            security = {
                    @SecurityRequirement(name = "bearerAuth")
            }
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "204",
                    description = "Product deactivated"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Authentication required"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "ADMIN role required"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Product not found"
            )
    })
    public ResponseEntity<Void> deactivateProduct(

            @Parameter(
                    description = "Product UUID",
                    required = true
            )
            @PathVariable UUID productId) {

        productService.deactivateProduct(productId);

        return ResponseEntity.noContent().build();
    }
}