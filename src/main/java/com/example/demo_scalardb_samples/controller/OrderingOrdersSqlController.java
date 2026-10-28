package com.example.demo_scalardb_samples.controller;

import com.example.demo_scalardb_samples.service.OrderingOrdersSqlService;
import com.example.demo_scalardb_samples.dto.OrderingOrdersDto;
import com.example.demo_scalardb_samples.dto.ApiResponse;
import com.example.demo_scalardb_samples.dto.ResponseStatusDto;
import com.example.demo_scalardb_samples.exception.CustomException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

@RequestMapping(value = "/ordering-orders-sql")
@RestController
public class OrderingOrdersSqlController {
    @Autowired
    private OrderingOrdersSqlService ordersSqlService;

    // INSERT using SQL
    @PostMapping
    public ResponseEntity<ApiResponse<Void>> insertOrderingOrders(@RequestBody OrderingOrdersDto ordersDto) {
        ResponseStatusDto status = ordersSqlService.insertOrderingOrders(ordersDto);
        return ResponseEntity.ok(ApiResponse.fromResponseStatus(status));
    }

    // UPSERT using SQL (insert or update if exists)
    @PostMapping("/upsert")
    public ResponseEntity<ApiResponse<Void>> upsertOrderingOrders(@RequestBody OrderingOrdersDto ordersDto) {
        ResponseStatusDto status = ordersSqlService.upsertOrderingOrders(ordersDto);
        return ResponseEntity.ok(ApiResponse.fromResponseStatus(status));
    }

    // SELECT using SQL (get by key)
    @GetMapping("/{order_id}")
    public ResponseEntity<ApiResponse<OrderingOrdersDto>> getOrderingOrders(@PathVariable("order_id") String order_id) {
        OrderingOrdersDto ordersDto = OrderingOrdersDto.builder()
            .orderId(order_id)
            .build();
        OrderingOrdersDto result = ordersSqlService.getOrderingOrders(ordersDto);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // UPDATE using SQL
    @PutMapping
    public ResponseEntity<ApiResponse<Void>> updateOrderingOrders(@RequestBody OrderingOrdersDto ordersDto) {
        ResponseStatusDto status = ordersSqlService.updateOrderingOrders(ordersDto);
        return ResponseEntity.ok(ApiResponse.fromResponseStatus(status));
    }

    // DELETE using SQL
    @DeleteMapping("/{order_id}")
    public ResponseEntity<ApiResponse<Void>> deleteOrderingOrders(@PathVariable("order_id") String order_id) {
        OrderingOrdersDto ordersDto = OrderingOrdersDto.builder()
            .orderId(order_id)
            .build();
        ResponseStatusDto status = ordersSqlService.deleteOrderingOrders(ordersDto);
        return ResponseEntity.ok(ApiResponse.fromResponseStatus(status));
    }

    // SELECT with partition key using SQL (returns List)
    @GetMapping("/scan-by-pk/{order_id}")
    public ResponseEntity<ApiResponse<List<OrderingOrdersDto>>> getOrderingOrdersByPk(@PathVariable("order_id") String order_id) {
        OrderingOrdersDto ordersDto = OrderingOrdersDto.builder()
            .orderId(order_id)
            .build();
        List<OrderingOrdersDto> result = ordersSqlService.getOrderingOrdersListByPk(ordersDto);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // SELECT * (scan all) using SQL
    @GetMapping("/scan-all")
    public ResponseEntity<ApiResponse<List<OrderingOrdersDto>>> getOrderingOrdersListAll() {
        List<OrderingOrdersDto> result = ordersSqlService.getOrderingOrdersListAll();
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @ExceptionHandler(value = CustomException.class)
    public ResponseEntity<ApiResponse<Void>> handleScalarDbException(CustomException ex) {
        ApiResponse<Void> errorResponse = ApiResponse.error(ex.getErrorCode(), ex.getMessage());
        errorResponse.setTransactionId(ex.getTransactionId());
        return switch (ex.getErrorCode()) {
            case 9100, 9400 -> new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
            case 9200, 9300 -> new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
            case 9600 -> new ResponseEntity<>(errorResponse, HttpStatus.FORBIDDEN);
            case 9700 -> new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
            default -> new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
        };
    }
}
