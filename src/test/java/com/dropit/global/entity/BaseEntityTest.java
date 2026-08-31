package com.dropit.global.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class BaseEntityTest {

    @Test
    @DisplayName("엔티티 생성 시 생성 시각과 수정 시각을 함께 기록한다")
    void recordCreatedAndUpdatedTime() {
        TestEntity entity = new TestEntity();

        entity.onCreate();

        assertNotNull(entity.getCreatedAt());
        assertEquals(entity.getCreatedAt(), entity.getUpdatedAt());
    }

    @Test
    @DisplayName("엔티티 수정 시 생성 시각은 유지하고 수정 시각만 갱신한다")
    void updateOnlyUpdatedTime() {
        TestEntity entity = new TestEntity();
        entity.onCreate();
        LocalDateTime createdAt = entity.getCreatedAt();

        entity.onUpdate();

        assertEquals(createdAt, entity.getCreatedAt());
        assertFalse(entity.getUpdatedAt().isBefore(createdAt));
    }

    private static class TestEntity extends BaseEntity {
    }
}
