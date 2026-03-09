package com.apisix.controlplane.controller;

import com.apisix.controlplane.dto.CreateProductRequest;
import com.apisix.controlplane.dto.ProductResponse;
import com.apisix.controlplane.entity.Product;
import com.apisix.controlplane.service.ProductService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orgs/{orgId}/envs/{envId}/products")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Products")
@CrossOrigin(origins = "*")
public class ProductController {

    private final ProductService productService;

    @PostMapping
    public ResponseEntity<ProductResponse> createProduct(
            @PathVariable String orgId,
            @PathVariable String envId,
            @Valid @RequestBody CreateProductRequest request) {
        log.info("Creating product in org {} env {}", orgId, envId);
        Product product = productService.createProduct(orgId, envId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.toResponse(product));
    }

    @GetMapping
    public ResponseEntity<List<ProductResponse>> getAllProducts(
            @PathVariable String orgId,
            @PathVariable String envId) {
        log.info("Fetching products for org {} env {}", orgId, envId);
        List<Product> products = productService.getProductsByEnvironment(orgId, envId);
        return ResponseEntity.ok(productService.toResponseList(products));
    }

    @GetMapping("/{productId}")
    public ResponseEntity<ProductResponse> getProduct(
            @PathVariable String orgId,
            @PathVariable String envId,
            @PathVariable String productId) {
        log.info("Fetching product {} in org {} env {}", productId, orgId, envId);
        Product product = productService.getProductById(orgId, productId);
        return ResponseEntity.ok(productService.toResponse(product));
    }

    @PutMapping("/{productId}")
    public ResponseEntity<ProductResponse> updateProduct(
            @PathVariable String orgId,
            @PathVariable String envId,
            @PathVariable String productId,
            @Valid @RequestBody CreateProductRequest request) {
        log.info("Updating product {} in org {} env {}", productId, orgId, envId);
        Product product = productService.updateProduct(orgId, envId, productId, request);
        return ResponseEntity.ok(productService.toResponse(product));
    }

    @DeleteMapping("/{productId}")
    public ResponseEntity<Void> deleteProduct(
            @PathVariable String orgId,
            @PathVariable String envId,
            @PathVariable String productId,
            @RequestParam(required = false, defaultValue = "false") boolean force) {
        log.info("Deleting product {} in org {} env {} (force: {})", productId, orgId, envId, force);
        productService.deleteProduct(orgId, envId, productId, force);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{productId}/clone")
    public ResponseEntity<ProductResponse> cloneProduct(
            @PathVariable String orgId,
            @PathVariable String envId,
            @PathVariable String productId,
            @RequestParam String targetEnvId) {
        log.info("Cloning product {} from env {} to env {} in org {}", productId, envId, targetEnvId, orgId);
        Product cloned = productService.cloneProduct(orgId, envId, productId, targetEnvId);
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.toResponse(cloned));
    }
}
