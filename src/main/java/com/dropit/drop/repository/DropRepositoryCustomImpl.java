package com.dropit.drop.repository;

import com.dropit.drop.dto.request.DropSearchCondition;
import com.dropit.drop.dto.request.DropSortType;
import com.dropit.drop.entity.Drop;
import com.dropit.drop.entity.DropStatus;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

import static com.dropit.drop.entity.QDrop.drop;
import static com.dropit.product.entity.QProduct.product;
import static com.dropit.user.entity.QUser.user;

@RequiredArgsConstructor
public class DropRepositoryCustomImpl implements DropRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<Drop> searchPublicDrops(
            DropSearchCondition condition,
            Pageable pageable
    ) {
        LocalDateTime now = LocalDateTime.now();

        List<Drop> content = queryFactory
                .selectFrom(drop)
                .join(drop.product, product).fetchJoin()
                .join(product.seller, user).fetchJoin()
                .where(
                        drop.visible.isTrue(),
                        keywordContains(condition.keyword()),
                        statusEquals(condition.status(), now)
                )
                .orderBy(orderSpecifiers(condition.sortType()))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(drop.count())
                .from(drop)
                .join(drop.product, product)
                .join(product.seller, user)
                .where(
                        drop.visible.isTrue(),
                        keywordContains(condition.keyword()),
                        statusEquals(condition.status(), now)
                )
                .fetchOne();

        return new PageImpl<>(
                content,
                pageable,
                total != null ? total : 0L
        );
    }

    private BooleanExpression keywordContains(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }

        return product.name.containsIgnoreCase(keyword)
                .or(user.username.containsIgnoreCase(keyword));
    }

    private BooleanExpression statusEquals(
            DropStatus status,
            LocalDateTime now
    ) {
        if (status == null) {
            return null;
        }

        return switch (status) {
            case READY -> drop.openAt.gt(now);
            case OPEN -> drop.openAt.loe(now)
                    .and(drop.closeAt.gt(now))
                    .and(drop.remainingQuantity.gt(0));
            case SOLDOUT -> drop.openAt.loe(now)
                    .and(drop.closeAt.gt(now))
                    .and(drop.remainingQuantity.eq(0));
            case CLOSED -> drop.closeAt.loe(now);
        };
    }

    private OrderSpecifier<?>[] orderSpecifiers(DropSortType sortType) {
        if (sortType == null) {
            return latestOrder();
        }

        return switch (sortType) {
            case LATEST -> latestOrder();
            case PRICE_ASC -> new OrderSpecifier<?>[]{
                    drop.price.asc(),
                    drop.id.desc()
            };
            case CLOSING_SOON -> new OrderSpecifier<?>[]{
                    drop.closeAt.asc(),
                    drop.id.desc()
            };
        };
    }

    private OrderSpecifier<?>[] latestOrder() {
        return new OrderSpecifier<?>[]{
                drop.createdAt.desc(),
                drop.id.desc()
        };
    }
}
