package com.apisix.controlplane.controller;

import com.apisix.controlplane.dto.CreateProductRequest;
import com.apisix.controlplane.entity.Product;
import com.apisix.controlplane.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/organizations/{orgId}/products")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class ProductController {
    
    private final ProductService productService;
    
    @PostMapping
    public ResponseEntity<Product> createProduct(
            @PathVariable String orgId,
            @Valid @RequestBody CreateProductRequest request) {
        log.info("POST /api/v1/organizations/{}/products - Creating product", orgId);
        Product product = productService.createProduct(orgId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(product);
    }
    
    @GetMapping
    public ResponseEntity<List<Product>> getAllProducts(@PathVariable String orgId) {
        log.info("GET /api/v1/organizations/{}/products - Fetching all products", orgId);
        List<Product> products = productService.getProductsByOrganization(orgId);
        return ResponseEntity.ok(products);
    }
    
    @GetMapping("/{productId}")
    public ResponseEntity<Product> getProduct(
            @PathVariable String orgId,
            @PathVariable String productId) {
        log.info("GET /api/v1/organizations/{}/products/{} - Fetching product", orgId, productId);
        Product product = productService.getProductById(orgId, productId);
        return ResponseEntity.ok(product);
    }
    
    @PutMapping("/{productId}")
    public ResponseEntity<Product> updateProduct(
            @PathVariable String orgId,
            @PathVariable String productId,
            @Valid @RequestBody CreateProductRequest request) {
        log.info("PUT /api/v1/organizations/{}/products/{} - Updating product", orgId, productId);
        Product product = productService.updateProduct(orgId, productId, request);
        return ResponseEntity.ok(product);
    }
    
    @DeleteMapping("/{productId}")
    public ResponseEntity<Void> deleteProduct(
            @PathVariable String orgId,
            @PathVariable String productId,
            @RequestParam(required = false, defaultValue = "false") boolean force) {
        log.info("DELETE /api/v1/organizations/{}/products/{} - Deleting product (force: {})", orgId, productId, force);
        productService.deleteProduct(orgId, productId, force);
        return ResponseEntity.noContent().build();
    }
}

