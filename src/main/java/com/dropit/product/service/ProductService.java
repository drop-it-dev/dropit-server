package com.dropit.product.service;

import com.dropit.product.dto.request.ProductCreateRequest;
import com.dropit.product.dto.response.ProductResponse;
import com.dropit.product.entity.Product;
import com.dropit.product.repository.ProductRepository;
import com.dropit.user.entity.User;
import com.dropit.user.entity.UserRole;
import com.dropit.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    @Transactional
    public ProductResponse createProduct(Long sellerId, ProductCreateRequest request) {
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

        return new ProductResponse(savedProduct);
    }
}
