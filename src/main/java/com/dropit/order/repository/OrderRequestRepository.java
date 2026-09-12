package com.dropit.order.repository;

import com.dropit.order.entity.OrderRequest;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRequestRepository extends JpaRepository<OrderRequest, String> {
}
