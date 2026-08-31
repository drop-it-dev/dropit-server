package com.dropit.product.controller;

import com.dropit.product.dto.request.ProductCreateRequest;
import com.dropit.product.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
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
}
