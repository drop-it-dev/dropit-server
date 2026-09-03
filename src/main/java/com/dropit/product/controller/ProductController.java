package com.dropit.product.controller;

import com.dropit.product.dto.request.ProductCreateRequest;
import com.dropit.product.dto.request.ProductUpdateRequest;
import com.dropit.product.dto.response.ProductResponse;
import com.dropit.product.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
@RestController
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @PostMapping("/products")
    public ResponseEntity<Long> create(
            @RequestParam Long sellerId,
            @Valid @RequestBody ProductCreateRequest request
    ) {
        Long productId = productService.create(sellerId, request);

        return ResponseEntity
                .created(URI.create("/products/" + productId))
                .body(productId);
    }

    @GetMapping("/products/{productId}")
    public ResponseEntity<ProductResponse> getProduct(@PathVariable Long productId) {
        ProductResponse response = productService.getProduct(productId);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/products")
    public ResponseEntity<Page<ProductResponse>> getProducts(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        Page<ProductResponse> response = productService.getProducts(pageable);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/sellers/{sellerId}/products")
    public ResponseEntity<Page<ProductResponse>> getProductsBySeller(
            @PathVariable Long sellerId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        Page<ProductResponse> response = productService.getProductsBySeller(sellerId, pageable);

        return ResponseEntity.ok(response);
    }

    @PutMapping("/products/{productId}")
    public ResponseEntity<ProductResponse> update(
            @RequestParam Long sellerId,
            @PathVariable Long productId,
            @Valid @RequestBody ProductUpdateRequest request
    ) {
        ProductResponse response = productService.update(sellerId, productId, request);

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/products/{productId}")
    public ResponseEntity<Void> delete(
            @RequestParam Long sellerId,
            @PathVariable Long productId
    ) {
        productService.delete(sellerId, productId);

        return ResponseEntity.noContent().build();
    }
}
