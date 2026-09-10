package com.dropit.product.service;

import com.dropit.drop.repository.DropRepository;
import com.dropit.global.exception.ServiceException;
import com.dropit.global.storage.S3ImageService;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final DropRepository dropRepository;
    private final S3ImageService s3ImageService;

    @Transactional
    public Long create(Long sellerId, ProductCreateRequest request) {
        User seller = userRepository.findById(sellerId)
                .orElseThrow(() ->
                        new ServiceException(ProductErrorCode.SELLER_NOT_FOUND)
                );

        if (seller.getRole() != UserRole.SELLER) {
            throw new ServiceException(
                    ProductErrorCode.SELLER_ROLE_REQUIRED
            );
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
                .orElseThrow(() ->
                        new ServiceException(
                                ProductErrorCode.PRODUCT_NOT_FOUND
                        )
                );

        return toResponse(product);
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> getProducts(Pageable pageable) {
        Page<Product> products = productRepository.findAll(pageable);

        return products.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> getProductsBySeller(
            Long sellerId,
            Pageable pageable
    ) {
        User seller = userRepository.findById(sellerId)
                .orElseThrow(() ->
                        new ServiceException(
                                ProductErrorCode.SELLER_NOT_FOUND
                        )
                );

        if (seller.getRole() != UserRole.SELLER) {
            throw new ServiceException(
                    ProductErrorCode.SELLER_NOT_FOUND
            );
        }

        Page<Product> products =
                productRepository.findAllBySellerId(sellerId, pageable);

        return products.map(this::toResponse);
    }

    @Transactional
    public ProductResponse update(
            Long sellerId,
            Long productId,
            ProductUpdateRequest request
    ) {
        Product product = findOwnedProduct(sellerId, productId);

        product.updateInfo(
                request.getName(),
                request.getDescription()
        );

        return toResponse(product);
    }

    @Transactional
    public ProductResponse uploadImage(
            Long sellerId,
            Long productId,
            MultipartFile file
    ) {
        /*
         * Check ownership before uploading anything to S3.
         */
        Product product = findOwnedProduct(sellerId, productId);

        String oldKey = product.getImageUrl();

        String newKey = s3ImageService.upload(
                file,
                "products/" + productId
        );

        product.changeImage(newKey);

        synchronizeImageReplacement(oldKey, newKey);

        /*
         * This method handles one product, so return one ProductResponse.
         */
        return toResponse(product);
    }

    @Transactional
    public void delete(Long sellerId, Long productId) {
        Product product = findOwnedProductForUpdate(sellerId, productId);

        if (dropRepository.existsByProductId(productId)) {
            throw new ServiceException(
                    ProductErrorCode.PRODUCT_IN_USE_BY_DROP
            );
        }

        String imageKey = product.getImageUrl();

        productRepository.delete(product);

        deleteImageAfterCommit(imageKey);
    }

    @Transactional
    public void deleteImage(Long sellerId, Long productId) {
        Product product = findOwnedProduct(sellerId, productId);

        String imageKey = product.getImageUrl();
        product.changeImage(null);
        deleteImageAfterCommit(imageKey);
    }

    private Product findOwnedProduct(
            Long sellerId,
            Long productId
    ) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() ->
                        new ServiceException(
                                ProductErrorCode.PRODUCT_NOT_FOUND
                        )
                );

        validateOwner(sellerId, product);

        return product;
    }

    private Product findOwnedProductForUpdate(
            Long sellerId,
            Long productId
    ) {
        Product product = productRepository.findByIdForUpdate(productId)
                .orElseThrow(() ->
                        new ServiceException(
                                ProductErrorCode.PRODUCT_NOT_FOUND
                        )
                );

        validateOwner(sellerId, product);

        return product;
    }

    private void validateOwner(Long sellerId, Product product) {
        if (!product.getSeller().getId().equals(sellerId)) {
            throw new ServiceException(
                    ProductErrorCode.PRODUCT_OWNER_REQUIRED
            );
        }
      
        if (product.getSeller().getRole() != UserRole.SELLER) {
            throw new ServiceException(
                    ProductErrorCode.SELLER_ROLE_REQUIRED
            );
        }
    }

    private ProductResponse toResponse(Product product) {
        String imageUrl = s3ImageService.createDownloadUrl(
                product.getImageUrl()
        );

        return new ProductResponse(product, imageUrl);
    }

    private void synchronizeImageReplacement(
            String oldKey,
            String newKey
    ) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {

                    @Override
                    public void afterCommit() {
                        s3ImageService.deleteQuietly(oldKey);
                    }

                    @Override
                    public void afterCompletion(int status) {
                        if (status != STATUS_COMMITTED) {
                            s3ImageService.deleteQuietly(newKey);
                        }
                    }
                }
        );
    }

    private void deleteImageAfterCommit(String key) {
        if (key == null) {
            return;
        }

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {

                    @Override
                    public void afterCommit() {
                        s3ImageService.deleteQuietly(key);
                    }
                }
        );
    }
}
