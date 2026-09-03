package com.dropit.product.service;

import com.dropit.global.exception.ServiceException;
import com.dropit.product.dto.request.ProductCreateRequest;
import com.dropit.product.dto.request.ProductUpdateRequest;
import com.dropit.product.dto.response.ProductResponse;
import com.dropit.product.entity.Product;
import com.dropit.product.exception.ProductErrorCode;
import com.dropit.product.repository.ProductRepository;
import com.dropit.user.entity.User;
import com.dropit.user.entity.UserRole;
import com.dropit.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    @Transactional
    public Long create(Long sellerId, ProductCreateRequest request) {
        User seller = userRepository.findById(sellerId)
                .orElseThrow(() -> new ServiceException(ProductErrorCode.SELLER_NOT_FOUND));

        if (seller.getRole() != UserRole.SELLER) {
            throw new ServiceException(ProductErrorCode.SELLER_ROLE_REQUIRED);
        }

        Product product = new Product(
                seller,
                request.getName(),
                request.getDescription(),
                null
        );

        Product savedProduct = productRepository.save(product);

        return savedProduct.getId();
    }

    @Transactional(readOnly = true)
    public ProductResponse getProduct(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ServiceException(ProductErrorCode.PRODUCT_NOT_FOUND));

        return new ProductResponse(product);
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> getProducts(Pageable pageable) {
        Page<Product> products = productRepository.findAll(pageable);

        return products.map(product -> new ProductResponse(product));
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> getProductsBySeller(Long sellerId, Pageable pageable) {
        User seller = userRepository.findById(sellerId)
                .orElseThrow(() -> new ServiceException(ProductErrorCode.SELLER_NOT_FOUND));

        if (seller.getRole() != UserRole.SELLER) {
            throw new ServiceException(ProductErrorCode.SELLER_NOT_FOUND);
        }

        Page<Product> products = productRepository.findAllBySellerId(sellerId, pageable);

        return products.map(product -> new ProductResponse(product));
    }

    @Transactional
    public ProductResponse update(
            Long sellerId,
            Long productId,
            ProductUpdateRequest request
    ) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ServiceException(ProductErrorCode.PRODUCT_NOT_FOUND));

        if (!product.getSeller().getId().equals(sellerId)) {
            throw new ServiceException(ProductErrorCode.PRODUCT_OWNER_REQUIRED);
        }

        product.updateInfo(
                request.getName(),
                request.getDescription()
        );

        return new ProductResponse(product);
    }

    @Transactional
    public void delete(Long sellerId, Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ServiceException(ProductErrorCode.PRODUCT_NOT_FOUND));

        if (!product.getSeller().getId().equals(sellerId)) {
            throw new ServiceException(ProductErrorCode.PRODUCT_OWNER_REQUIRED);
        }

        productRepository.delete(product);
    }
}
