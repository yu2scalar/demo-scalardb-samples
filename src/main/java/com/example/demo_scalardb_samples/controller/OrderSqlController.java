package com.example.demo_scalardb_samples.controller;

import com.example.demo_scalardb_samples.dto.ApiResponse;
import com.example.demo_scalardb_samples.dto.OrderDetailDto;
import com.example.demo_scalardb_samples.dto.PlaceOrderRequestDto;
import com.example.demo_scalardb_samples.dto.PlaceOrderResponseDto;
import com.example.demo_scalardb_samples.dto.ResponseStatusDto;
import com.example.demo_scalardb_samples.exception.CustomException;
import com.example.demo_scalardb_samples.service.OrderSqlService;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// Business logic on the SQL API
@RestController
public class OrderSqlController {
    @Autowired
    private OrderSqlService orderSqlService;

    @PostMapping("/orders-sql")
    public ResponseEntity<ApiResponse<PlaceOrderResponseDto>> placeOrder(@RequestBody PlaceOrderRequestDto request) {
        return ResponseEntity.ok(ApiResponse.success(orderSqlService.placeOrder(request)));
    }

    @GetMapping("/orders-sql/{order_id}")
    public ResponseEntity<ApiResponse<OrderDetailDto>> getOrder(@PathVariable("order_id") String order_id) {
        return ResponseEntity.ok(ApiResponse.success(orderSqlService.getOrder(order_id)));
    }

    @PostMapping("/orders-sql/{order_id}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancelOrder(@PathVariable("order_id") String order_id) {
        ResponseStatusDto status = orderSqlService.cancelOrder(order_id);
        return ResponseEntity.ok(ApiResponse.fromResponseStatus(status));
    }

    @GetMapping("/customers-sql/{customer_id}/orders")
    public ResponseEntity<ApiResponse<List<OrderDetailDto>>> getOrders(@PathVariable("customer_id") int customer_id) {
        return ResponseEntity.ok(ApiResponse.success(orderSqlService.getOrdersByCustomerId(customer_id)));
    }

    @ExceptionHandler(value = CustomException.class)
    public ResponseEntity<ApiResponse<Void>> handleScalarDbException(CustomException ex) {
        return OrderController.errorResponse(ex);
    }
}
