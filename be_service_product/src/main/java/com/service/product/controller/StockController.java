package com.service.product.controller;

import com.service.product.kafka.StockService;
import com.service.product.module.dto.stock.CheckStockRequest;
import com.service.product.module.dto.stock.CheckStockResponse;
import com.service.product.module.dto.stock.ReserveStockRequest;
import com.service.product.module.dto.stock.ReserveStockResponse;
import com.service.product.module.dto.stock.RollbackStockRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller cho Quản lý Tồn kho & Xác nhận Đơn hàng (Stock Management).
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/product/stock")
@RequiredArgsConstructor
public class StockController {

    private final StockService stockService;

    /**
     * API Giữ tồn kho & Cập nhật trừ số lượng kho trong bảng Product.
     *
     * POST /api/v1/product/stock/reserve
     * POST /api/v1/product/stock/stock-reserved
     * Body: ReserveStockRequest
     */
    @PostMapping({"/reserve", "/stock-reserved"})
    public ResponseEntity<ReserveStockResponse> reserveStock(@Valid @RequestBody ReserveStockRequest request) {
        log.info("[Product-Stock] Nhận yêu cầu giữ tồn kho và trừ stock cho orderId={}", request.getOrderId());
        ReserveStockResponse response = stockService.reserveStockApi(request);
        if (Boolean.TRUE.equals(response.getSuccess())) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    /**
     * API Hoàn trả tồn kho (Rollback) khi đơn hàng bị hủy hoặc thanh toán thất bại.
     *
     * POST /api/v1/product/stock/rollback
     * Body: RollbackStockRequest
     */
    @PostMapping("/rollback")
    public ResponseEntity<ReserveStockResponse> rollbackStock(@Valid @RequestBody RollbackStockRequest request) {
        log.info("[Product-Stock] Nhận yêu cầu rollback tồn kho cho orderId={}", request.getOrderId());
        ReserveStockResponse response = stockService.rollbackStockApi(request);
        return ResponseEntity.ok(response);
    }

    /**
     * API Kiểm tra nhanh số lượng tồn kho trước khi đặt hàng (Không trừ kho).
     *
     * POST /api/v1/product/stock/check
     * Body: CheckStockRequest
     */
    @PostMapping("/check")
    public ResponseEntity<CheckStockResponse> checkStock(@Valid @RequestBody CheckStockRequest request) {
        log.info("[Product-Stock] Nhận yêu cầu kiểm tra tồn kho cho {} sản phẩm", request.getItems().size());
        CheckStockResponse response = stockService.checkStockApi(request);
        return ResponseEntity.ok(response);
    }
}
