package com.dropit.drop.repository;

import com.dropit.drop.dto.request.DropSearchCondition;
import com.dropit.drop.dto.request.DropSortType;
import com.dropit.drop.entity.Drop;
import com.dropit.drop.entity.DropStatus;
import com.dropit.global.storage.S3ImageService;
import com.dropit.product.entity.Product;
import com.dropit.user.entity.User;
import com.dropit.user.entity.UserRole;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** 로컬 MySQL에서 실행하며 픽스처는 트랜잭션 롤백으로 정리한다. */
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=none")
@Transactional
class DropSearchIntegrationTest {
    @Autowired EntityManager em;
    @Autowired DropRepository repository;
    @MockitoBean S3ImageService s3ImageService;

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
}
