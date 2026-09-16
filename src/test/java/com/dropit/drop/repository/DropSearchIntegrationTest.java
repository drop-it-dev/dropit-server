package com.dropit.drop.repository;

import com.dropit.drop.dto.request.DropSearchCondition;
import com.dropit.drop.dto.request.DropSortType;
import com.dropit.drop.entity.Drop;
import com.dropit.drop.entity.DropStatus;
import com.dropit.global.config.QuerydslConfig;
import com.dropit.product.entity.Product;
import com.dropit.user.entity.User;
import com.dropit.user.entity.UserRole;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.mysql.MySQLContainer;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(QuerydslConfig.class)
@Transactional
class DropSearchIntegrationTest {
    private static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    static {
        MYSQL.start();
    }

    @Autowired EntityManager em;
    @Autowired DropRepository repository;

    @DynamicPropertySource
    static void registerDatasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Test
    void keywordPaginationKeepsOrderCountAndFetchJoins() {
        String marker = "search-" + UUID.randomUUID();
        User seller = new User(marker + "@test.invalid", "unused", marker, UserRole.SELLER);
        em.persist(seller);
        Product product = new Product(seller, marker, "fixture", null);
        em.persist(product);
        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < 5; i++) {
            Drop drop = new Drop(product, BigDecimal.valueOf(100 + i), 10, 0, 1,
                    now.minusDays(1), now.plusDays(1));
            drop.changeVisibility(i != 4);
            em.persist(drop);
        }
        em.flush();
        em.clear();
        var condition = new DropSearchCondition(marker, DropStatus.OPEN, DropSortType.CLOSING_SOON);
        var first = repository.searchPublicDrops(condition, PageRequest.of(0, 2));
        var second = repository.searchPublicDrops(condition, PageRequest.of(1, 2));
        assertEquals(4, first.getTotalElements());
        assertEquals(2, first.getNumberOfElements());
        assertEquals(2, second.getNumberOfElements());
        assertTrue(first.getContent().getLast().getId() > second.getContent().getFirst().getId());
        assertEquals(marker, first.getContent().getFirst().getProduct().getSeller().getUsername());
        var empty = repository.searchPublicDrops(condition, PageRequest.of(2, 2));
        assertTrue(empty.isEmpty());
        assertEquals(4, empty.getTotalElements());
    }

    @Test
    void noKeywordMatchesOriginalJoinQuery() {
        // 시간 경계에 영향을 받지 않도록 공개 여부만 비교한다.
        var actual = repository.searchPublicDrops(
                new DropSearchCondition(null, null, DropSortType.CLOSING_SOON), PageRequest.of(0, 20));
        List<Long> expected = em.createQuery("select d.id from Drop d join d.product p join p.seller s "
                + "where d.visible = true order by d.closeAt asc, d.id desc", Long.class)
                .setMaxResults(20).getResultList();
        Long count = em.createQuery("select count(d) from Drop d join d.product p join p.seller s "
                + "where d.visible = true", Long.class).getSingleResult();
        assertEquals(expected, actual.getContent().stream().map(Drop::getId).toList());
        assertEquals(count.longValue(), actual.getTotalElements());
    }

    @Test
    void liveStateReadsCommittedStockAndVisibility() {
        String marker = "live-state-" + UUID.randomUUID();
        User seller = new User(marker + "@test.invalid", "unused", marker, UserRole.SELLER);
        em.persist(seller);
        Product product = new Product(seller, marker, "fixture", null);
        em.persist(product);
        LocalDateTime now = LocalDateTime.now();
        Drop drop = new Drop(product, BigDecimal.valueOf(100), 10, 0, 1,
                now.minusDays(1), now.plusDays(1));
        drop.changeVisibility(true);
        drop.decreaseStock(3, now);
        em.persist(drop);
        em.flush();
        em.clear();

        DropLiveState result = repository.findLiveStateById(drop.getId()).orElseThrow();

        assertEquals(7, result.getRemainingQuantity());
        assertTrue(result.isVisible());
    }
}
