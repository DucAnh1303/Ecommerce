package com.service.product.config.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * Feign Client gọi sang Order Service để đồng bộ trạng thái đơn hàng.
 * Tự động chuyển tiếp Authorization Header thông qua FeignClientInterceptor
 * hoặc truyền trực tiếp qua tham số header.
 */
@FeignClient(
        name = "service-order",
        url = "${app.order-service.url:http://localhost:8087}",
        configuration = FeignClientInterceptor.class
)
public interface OrderFeignClient {

    /** Cập nhật trạng thái đơn hàng sang STOCK_RESERVED */
    @PostMapping("/api/v1/order/{orderId}/stock-reserved")
    Object markStockReserved(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long orderId
    );
}
