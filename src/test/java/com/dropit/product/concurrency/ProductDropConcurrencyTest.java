package com.dropit.product.concurrency;

import com.dropit.drop.dto.request.DropCreateRequest;
import com.dropit.drop.repository.DropRepository;
import com.dropit.drop.service.DropService;
import com.dropit.global.config.QuerydslConfig;
import com.dropit.global.exception.ServiceException;
import com.dropit.global.storage.S3ImageService;
import com.dropit.product.entity.Product;
import com.dropit.product.exception.ProductErrorCode;
import com.dropit.product.repository.ProductRepository;
import com.dropit.product.service.ProductService;
import com.dropit.user.entity.User;
import com.dropit.user.entity.UserRole;
import com.dropit.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.mysql.MySQLContainer;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ProductService.class, DropService.class, QuerydslConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ProductDropConcurrencyTest {

    private static final int REPEAT_COUNT = 100;
    private static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    static {
        MYSQL.start();
    }

    @Autowired
    private ProductService productService;

    @Autowired
    private DropService dropService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private DropRepository dropRepository;

    @MockitoBean
    private S3ImageService s3ImageService;

    @DynamicPropertySource
    static void registerDatasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @AfterEach
    void cleanUp() {
        dropRepository.deleteAllInBatch();
        productRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("비관적 락으로 상품 삭제와 Drop 생성을 순차 처리한다")
    void controlProductDeleteAndDropCreateWithPessimisticLock() throws Exception {
        User seller = userRepository.saveAndFlush(new User(
                "concurrency-seller@dropit.test",
                "encoded-password",
                "concurrency-seller",
                UserRole.SELLER
        ));

        AtomicInteger deleteSuccess = new AtomicInteger();
        AtomicInteger createSuccess = new AtomicInteger();
        AtomicInteger expectedConflict = new AtomicInteger();
        AtomicInteger unexpectedFailure = new AtomicInteger();
        AtomicInteger orphanDrop = new AtomicInteger();
        Map<String, AtomicInteger> expectedTypes = new ConcurrentHashMap<>();
        Map<String, AtomicInteger> unexpectedTypes = new ConcurrentHashMap<>();

        long startedAt = System.nanoTime();

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            for (int attempt = 0; attempt < REPEAT_COUNT; attempt++) {
                Product product = productRepository.saveAndFlush(new Product(
                        seller,
                        "Concurrency Product " + attempt,
                        "Before lock measurement",
                        null
                ));

                CountDownLatch ready = new CountDownLatch(2);
                CountDownLatch start = new CountDownLatch(1);

                Future<OperationResult> deleteFuture = executor.submit(() -> {
                    awaitStart(ready, start);
                    return executeDelete(seller.getId(), product.getId());
                });

                Future<OperationResult> createFuture = executor.submit(() -> {
                    awaitStart(ready, start);
                    return executeCreate(seller.getId(), product.getId());
                });

                assertTrue(ready.await(5, TimeUnit.SECONDS));
                start.countDown();

                countResult(
                        deleteFuture.get(10, TimeUnit.SECONDS),
                        deleteSuccess,
                        expectedConflict,
                        unexpectedFailure,
                        expectedTypes,
                        unexpectedTypes
                );
                countResult(
                        createFuture.get(10, TimeUnit.SECONDS),
                        createSuccess,
                        expectedConflict,
                        unexpectedFailure,
                        expectedTypes,
                        unexpectedTypes
                );

                boolean productExists = productRepository.existsById(product.getId());
                boolean dropExists = dropRepository.existsByProductId(product.getId());

                if (!productExists && dropExists) {
                    orphanDrop.incrementAndGet();
                }

                dropRepository.deleteAllInBatch();
                productRepository.deleteById(product.getId());
                productRepository.flush();
            }
        }

        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(
                System.nanoTime() - startedAt
        );

        System.out.printf(
                "CONCURRENCY_AFTER repeat=%d deleteSuccess=%d createSuccess=%d "
                        + "expectedConflict=%d unexpectedFailure=%d orphanDrop=%d "
                        + "elapsedMs=%d expectedTypes=%s unexpectedTypes=%s%n",
                REPEAT_COUNT,
                deleteSuccess.get(),
                createSuccess.get(),
                expectedConflict.get(),
                unexpectedFailure.get(),
                orphanDrop.get(),
                elapsedMillis,
                expectedTypes,
                unexpectedTypes
        );

        assertEquals(REPEAT_COUNT, deleteSuccess.get() + createSuccess.get());
        assertEquals(REPEAT_COUNT, expectedConflict.get());
        assertEquals(0, unexpectedFailure.get());
        assertEquals(REPEAT_COUNT * 2,
                deleteSuccess.get()
                        + createSuccess.get()
                        + expectedConflict.get()
                        + unexpectedFailure.get());
        assertEquals(0, orphanDrop.get());
    }

    private OperationResult executeDelete(Long sellerId, Long productId) {
        try {
            productService.delete(sellerId, productId);
            return OperationResult.success();
        } catch (RuntimeException exception) {
            return classify(exception);
        }
    }

    private OperationResult executeCreate(Long sellerId, Long productId) {
        try {
            dropService.save(sellerId, createRequest(productId));
            return OperationResult.success();
        } catch (RuntimeException exception) {
            return classify(exception);
        }
    }

    private OperationResult classify(RuntimeException exception) {
        if (exception instanceof ServiceException serviceException
                && (serviceException.getErrorCode() == ProductErrorCode.PRODUCT_NOT_FOUND
                || serviceException.getErrorCode() == ProductErrorCode.PRODUCT_IN_USE_BY_DROP)) {
            return OperationResult.expectedConflict(
                    serviceException.getErrorCode().code()
            );
        }

        return OperationResult.unexpected(exception.getClass().getSimpleName());
    }

    private void countResult(
            OperationResult result,
            AtomicInteger success,
            AtomicInteger expectedConflict,
            AtomicInteger unexpectedFailure,
            Map<String, AtomicInteger> expectedTypes,
            Map<String, AtomicInteger> unexpectedTypes
    ) {
        if (result.status() == ResultStatus.SUCCESS) {
            success.incrementAndGet();
            return;
        }

        if (result.status() == ResultStatus.EXPECTED_CONFLICT) {
            expectedConflict.incrementAndGet();
            expectedTypes.computeIfAbsent(
                    result.exceptionType(),
                    key -> new AtomicInteger()
            ).incrementAndGet();
            return;
        }

        unexpectedFailure.incrementAndGet();
        unexpectedTypes.computeIfAbsent(
                result.exceptionType(),
                key -> new AtomicInteger()
        ).incrementAndGet();
    }

    private void awaitStart(
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException {
        ready.countDown();
        start.await();
    }

    private DropCreateRequest createRequest(Long productId) {
        LocalDateTime openAt = LocalDateTime.now().plusDays(1);

        return new DropCreateRequest(
                productId,
                new BigDecimal("59000"),
                100,
                0,
                1,
                openAt,
                openAt.plusDays(1)
        );
    }

    private enum ResultStatus {
        SUCCESS,
        EXPECTED_CONFLICT,
        UNEXPECTED_FAILURE
    }

    private record OperationResult(
            ResultStatus status,
            String exceptionType
    ) {
        private static OperationResult success() {
            return new OperationResult(ResultStatus.SUCCESS, null);
        }

        private static OperationResult expectedConflict(String errorCode) {
            return new OperationResult(ResultStatus.EXPECTED_CONFLICT, errorCode);
        }

        private static OperationResult unexpected(String exceptionType) {
            return new OperationResult(ResultStatus.UNEXPECTED_FAILURE, exceptionType);
        }
    }
}
