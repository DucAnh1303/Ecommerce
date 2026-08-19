package com.service.order.repository;

import com.service.order.entity.OrderEntity;
import com.service.order.entity.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<OrderEntity, Long> {
    List<OrderEntity> findByUserId(Long userId);
    List<OrderEntity> findByStatus(OrderStatus status);
    List<OrderEntity> findByUserIdOrderByCreateTimeDesc(Long userId);
}
