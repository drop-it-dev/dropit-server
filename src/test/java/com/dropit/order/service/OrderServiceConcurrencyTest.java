package com.dropit.order.service;

import com.dropit.drop.entity.Drop;
import com.dropit.drop.repository.DropRepository;
import com.dropit.global.config.QuerydslConfig;
import com.dropit.order.dto.request.OrderCreateRequest;
import com.dropit.order.dto.request.OrderItemCreateRequest;
import com.dropit.order.entity.OrderItem;
import com.dropit.order.repository.OrderItemRepository;
import com.dropit.order.repository.OrderRepository;
import com.dropit.product.entity.Product;
import com.dropit.product.repository.ProductRepository;
import com.dropit.user.entity.User;
import com.dropit.user.entity.UserRole;
import com.dropit.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({OrderService.class, QuerydslConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OrderServiceConcurrencyTest {

    private static final String TRIGGER_NAME = "fail_order_item_insert_for_test";
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("dropit_test")
            .withUsername("test")
            .withPassword("test")
            .withCommand("mysqld", "--log-bin-trust-function-creators=1");

    static {
        MYSQL.start();
    }

    @Autowired private OrderService orderService;
    @Autowired private UserRepository userRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private DropRepository dropRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderItemRepository orderItemRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void registerDatasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @BeforeEach
    void cleanDatabase() {
        orderItemRepository.deleteAllInBatch();
        orderRepository.deleteAllInBatch();
        dropRepository.deleteAllInBatch();
        productRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @AfterEach
    void removeFailureTrigger() {
        jdbcTemplate.execute("DROP TRIGGER IF EXISTS " + TRIGGER_NAME);
    }

    @Test
    @DisplayName("재고 100개에 200명이 동시에 주문해도 100건만 확정된다")
    void preventOversellingWithConcurrentOrders() throws Exception {
        Fixture fixture = createFixture(100, 200);

        CallResult result = placeConcurrentOrders(fixture, 1, 32);

        assertEquals(100, result.successes());
        assertEquals(100, result.rejections());
        assertEquals(100, orderRepository.count());
        assertEquals(100, totalOrderedQuantity());
        assertEquals(0, remainingQuantity(fixture.drop()));
    }

    @Test
    @DisplayName("재고 10개에 수량 3 주문을 경합하면 3건만 확정되고 1개가 남는다")
    void preserveRemainingStockWhenConcurrentQuantityExceedsStock() throws Exception {
        Fixture fixture = createFixture(10, 4);

        CallResult result = placeConcurrentOrders(fixture, 3, 4);

        assertEquals(3, result.successes());
        assertEquals(1, result.rejections());
        assertEquals(3, orderRepository.count());
        assertEquals(9, totalOrderedQuantity());
        assertEquals(1, remainingQuantity(fixture.drop()));
    }

    @Test
    @DisplayName("주문 항목 저장에 실패하면 재고와 주문 데이터가 함께 rollback된다")
    void rollbackStockWhenOrderItemSaveFails() {
        Fixture fixture = createFixture(10, 1);
        jdbcTemplate.execute("""
                CREATE TRIGGER fail_order_item_insert_for_test
                BEFORE INSERT ON order_items
                FOR EACH ROW
                SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'forced test failure'
                """);

        assertThrows(
                RuntimeException.class,
                () -> orderService.create(
                        fixture.buyers().getFirst().getId(),
                        orderRequest(fixture.drop().getId(), 1)
                )
        );

        assertEquals(10, remainingQuantity(fixture.drop()));
        assertEquals(0, orderRepository.count());
        assertEquals(0, orderItemRepository.count());
    }

    private CallResult placeConcurrentOrders(Fixture fixture, int quantity, int workerCount) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(workerCount);
        CountDownLatch workersReady = new CountDownLatch(workerCount);
        CountDownLatch startSignal = new CountDownLatch(1);
        List<Future<CallResult>> futures = new ArrayList<>();

        try {
            for (int worker = 0; worker < workerCount; worker++) {
                int workerIndex = worker;
                futures.add(executor.submit(() -> {
                    workersReady.countDown();
                    assertTrue(startSignal.await(10, TimeUnit.SECONDS));

                    int successes = 0;
                    int rejections = 0;
                    for (int index = workerIndex; index < fixture.buyers().size(); index += workerCount) {
                        try {
                            orderService.create(
                                    fixture.buyers().get(index).getId(),
                                    orderRequest(fixture.drop().getId(), quantity)
                            );
                            successes++;
                        } catch (com.dropit.global.exception.ServiceException exception) {
                            rejections++;
                        }
                    }
                    return new CallResult(successes, rejections);
                }));
            }

            assertTrue(workersReady.await(10, TimeUnit.SECONDS));
            startSignal.countDown();

            int successes = 0;
            int rejections = 0;
            for (Future<CallResult> future : futures) {
                CallResult result = get(future);
                successes += result.successes();
                rejections += result.rejections();
            }
            return new CallResult(successes, rejections);
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    private CallResult get(Future<CallResult> future) throws Exception {
        try {
            return future.get(60, TimeUnit.SECONDS);
        } catch (ExecutionException exception) {
            throw new AssertionError("동시 주문 작업이 예외로 종료되었습니다.", exception.getCause());
        }
    }

    private Fixture createFixture(int stock, int buyerCount) {
        User seller = userRepository.saveAndFlush(
                new User("seller@example.com", "password", "seller", UserRole.SELLER)
        );
        Product product = productRepository.saveAndFlush(
                new Product(seller, "Limited Hoodie", "description", null)
        );
        LocalDateTime openAt = LocalDateTime.now().minusMinutes(1);
        Drop drop = new Drop(
                product,
                new BigDecimal("59000"),
                stock,
                20,
                0,
                openAt,
                openAt.plusHours(1)
        );
        drop.changeVisibility(true);
        drop = dropRepository.saveAndFlush(drop);

        List<User> buyers = new ArrayList<>();
        for (int index = 0; index < buyerCount; index++) {
            buyers.add(new User(
                    "buyer" + index + "@example.com",
                    "password",
                    "buyer" + index,
                    UserRole.USER
            ));
        }
        buyers = userRepository.saveAllAndFlush(buyers);
        return new Fixture(drop, buyers);
    }

    private OrderCreateRequest orderRequest(Long dropId, int quantity) {
        return new OrderCreateRequest(List.of(new OrderItemCreateRequest(dropId, quantity)));
    }

    private int remainingQuantity(Drop drop) {
        return dropRepository.findById(drop.getId()).orElseThrow().getRemainingQuantity();
    }

    private long totalOrderedQuantity() {
        return orderItemRepository.findAll().stream()
                .mapToLong(OrderItem::getQuantity)
                .sum();
    }

    private record Fixture(Drop drop, List<User> buyers) {
    }

    private record CallResult(int successes, int rejections) {
    }
}
