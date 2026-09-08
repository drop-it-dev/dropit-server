package com.dropit.product.controller;

import com.dropit.global.security.principal.CurrentUserId;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
@RestController
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @PostMapping("/products")
    public ResponseEntity<Long> create(
            @CurrentUserId Long sellerId,
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
            @CurrentUserId Long sellerId,
            @PathVariable Long productId,
            @Valid @RequestBody ProductUpdateRequest request
    ) {
        ProductResponse response = productService.update(sellerId, productId, request);

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/products/{productId}")
    public ResponseEntity<Void> delete(
            @CurrentUserId Long sellerId,
            @PathVariable Long productId
    ) {
        productService.delete(sellerId, productId);

        return ResponseEntity.noContent().build();
    }

    @PutMapping(
            value = "/products/{productId}/image",
            consumes = "multipart/form-data"
    )
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ProductResponse> uploadImage(
            @CurrentUserId Long sellerId,
            @PathVariable Long productId,
            @RequestParam("file") MultipartFile file
    ) {
        ProductResponse response =
                productService.uploadImage(sellerId, productId, file);

        return ResponseEntity.ok(response);
    }


}
