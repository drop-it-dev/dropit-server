package com.dropit.order.entity;

import com.dropit.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "drop_user_purchases",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_drop_user_purchases_drop_user",
                columnNames = {"drop_id", "user_id"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DropUserPurchase extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "drop_id", nullable = false)
    private Long dropId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "confirmed_quantity", nullable = false)
    private int confirmedQuantity;

    public DropUserPurchase(Long dropId, Long userId) {
        this.dropId = dropId;
        this.userId = userId;
    }
}
