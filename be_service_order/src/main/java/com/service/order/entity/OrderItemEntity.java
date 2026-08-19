package com.service.order.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Chi tiết từng sản phẩm trong đơn hàng.
 */
@Entity
@Table(name = "tb_order_item")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private OrderEntity order;

    /** ID sản phẩm từ Product Service */
    @Column(nullable = false)
    private Long productId;

    /** Tên sản phẩm (snapshot tại thời điểm order – không bị ảnh hưởng nếu tên sản phẩm thay đổi) */
    @Column(nullable = false)
    private String productName;

    /** Giá tại thời điểm order */
    @Column(nullable = false)
    private Long unitPrice;

    /** Số lượng mua */
    @Column(nullable = false)
    private Integer quantity;

    /** Thành tiền = unitPrice * quantity */
    @Column(nullable = false)
    private Long subtotal;
}
