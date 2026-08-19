package com.service.order.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Bảng đơn hàng chính. Mỗi Order có nhiều OrderItem (chi tiết sản phẩm đặt
 * mua).
 */
@Entity
@Table(name = "tb_order")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * userId lấy từ header X-User-Id do Gateway inject
     */
    @Column(nullable = false)
    private Long userId;

    /**
     * userName lấy từ header X-User-Name do Gateway inject
     */
    @Column(nullable = false)
    private String userName;

    /**
     * Tổng tiền đơn hàng
     */
    @Column(nullable = false)
    private Long totalAmount;

    /**
     * Trạng thái hiện tại của đơn hàng trong Saga
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private OrderStatus status = OrderStatus.PENDING;

    /**
     * Phương thức thanh toán (VNPAY, MOMO, CREDIT_CARD, BANK_TRANSFER, COD...)
     */
    @Column(name = "payment_method", length = 50)
    private String paymentMethod;

    /**
     * Mã giao dịch thanh toán từ cổng thanh toán
     */
    @Column(name = "transaction_id", length = 100)
    private String transactionId;

    /**
     * Số tiền thực tế đã thanh toán
     */
    @Column(name = "paid_amount")
    private Long paidAmount;

    /**
     * Thời điểm thanh toán thành công
     */
    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    /**
     * Lý do thất bại (nếu có)
     */
    @Column(length = 500)
    private String failReason;

    /**
     * Địa chỉ giao hàng
     */
    @Column(nullable = false)
    private String shippingAddress;

    /**
     * Ghi chú của khách hàng
     */
    private String note;

    /**
     * Danh sách sản phẩm trong đơn – cascade để tự động save/delete
     */
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<OrderItemEntity> items = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "create_time", nullable = false, updatable = false)
    private LocalDateTime createTime;

    @UpdateTimestamp
    @Column(name = "update_time", nullable = false)
    private LocalDateTime updateTime;
}
