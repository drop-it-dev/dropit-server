package com.dropit.product.service;

import com.dropit.product.dto.request.ProductCreateRequest;
import com.dropit.product.dto.request.ProductUpdateRequest;
import com.dropit.product.dto.response.ProductResponse;
import com.dropit.product.entity.Product;
import com.dropit.product.repository.ProductRepository;
import com.dropit.user.entity.User;
import com.dropit.user.entity.UserRole;
import com.dropit.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    @Transactional
    public Long create(Long sellerId, ProductCreateRequest request) {
        User seller = userRepository.findById(sellerId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

        if (seller.getRole() != UserRole.SELLER) {
            throw new IllegalStateException("판매자만 상품을 등록할 수 있습니다.");
        }

        Product product = new Product(
                seller,
                request.getName(),
                request.getPrice(),
                request.getDescription(),
                null
        );

        Product savedProduct = productRepository.save(product);

        return savedProduct.getId();
    }

    @Transactional(readOnly = true)
    public ProductResponse getProduct(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 상품입니다."));

        return new ProductResponse(product);
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> getProducts() {
        List<Product> products = productRepository.findAll();

        return products.stream()
                .map(product -> new ProductResponse(product))
                .toList();
    }

    @Transactional
    public ProductResponse update(
            Long sellerId,
            Long productId,
            ProductUpdateRequest request
    ) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 상품입니다."));

        if (!product.getSeller().getId().equals(sellerId)) {
            throw new IllegalStateException("상품 소유자만 수정할 수 있습니다.");
        }

        product.updateInfo(
                request.getName(),
                request.getPrice(),
                request.getDescription()
        );

        return new ProductResponse(product);
    }

    @Transactional
    public void delete(Long sellerId, Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 상품입니다."));

        if (!product.getSeller().getId().equals(sellerId)) {
            throw new IllegalStateException("상품 소유자만 삭제할 수 있습니다.");
        }

        productRepository.delete(product);
    }
}
