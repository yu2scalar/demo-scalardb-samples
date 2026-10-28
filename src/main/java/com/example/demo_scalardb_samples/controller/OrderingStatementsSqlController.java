package com.example.demo_scalardb_samples.controller;

import com.example.demo_scalardb_samples.service.OrderingStatementsSqlService;
import com.example.demo_scalardb_samples.dto.OrderingStatementsDto;
import com.example.demo_scalardb_samples.dto.ApiResponse;
import com.example.demo_scalardb_samples.dto.ResponseStatusDto;
import com.example.demo_scalardb_samples.exception.CustomException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

@RequestMapping(value = "/ordering-statements-sql")
@RestController
public class OrderingStatementsSqlController {
    @Autowired
    private OrderingStatementsSqlService statementsSqlService;

    // INSERT using SQL
    @PostMapping
    public ResponseEntity<ApiResponse<Void>> insertOrderingStatements(@RequestBody OrderingStatementsDto statementsDto) {
        ResponseStatusDto status = statementsSqlService.insertOrderingStatements(statementsDto);
        return ResponseEntity.ok(ApiResponse.fromResponseStatus(status));
    }

    // UPSERT using SQL (insert or update if exists)
    @PostMapping("/upsert")
    public ResponseEntity<ApiResponse<Void>> upsertOrderingStatements(@RequestBody OrderingStatementsDto statementsDto) {
        ResponseStatusDto status = statementsSqlService.upsertOrderingStatements(statementsDto);
        return ResponseEntity.ok(ApiResponse.fromResponseStatus(status));
    }

    // SELECT using SQL (get by key)
    @GetMapping("/{order_id}/{line_no}")
    public ResponseEntity<ApiResponse<OrderingStatementsDto>> getOrderingStatements(@PathVariable("order_id") String order_id, @PathVariable("line_no") Integer line_no) {
        OrderingStatementsDto statementsDto = OrderingStatementsDto.builder()
            .orderId(order_id)
                .lineNo(line_no)
            .build();
        OrderingStatementsDto result = statementsSqlService.getOrderingStatements(statementsDto);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // UPDATE using SQL
    @PutMapping
    public ResponseEntity<ApiResponse<Void>> updateOrderingStatements(@RequestBody OrderingStatementsDto statementsDto) {
        ResponseStatusDto status = statementsSqlService.updateOrderingStatements(statementsDto);
        return ResponseEntity.ok(ApiResponse.fromResponseStatus(status));
    }

    // DELETE using SQL
    @DeleteMapping("/{order_id}/{line_no}")
    public ResponseEntity<ApiResponse<Void>> deleteOrderingStatements(@PathVariable("order_id") String order_id, @PathVariable("line_no") Integer line_no) {
        OrderingStatementsDto statementsDto = OrderingStatementsDto.builder()
            .orderId(order_id)
                .lineNo(line_no)
            .build();
        ResponseStatusDto status = statementsSqlService.deleteOrderingStatements(statementsDto);
        return ResponseEntity.ok(ApiResponse.fromResponseStatus(status));
    }

    // SELECT with partition key using SQL (returns List)
    @GetMapping("/scan-by-pk/{order_id}")
    public ResponseEntity<ApiResponse<List<OrderingStatementsDto>>> getOrderingStatementsByPk(@PathVariable("order_id") String order_id) {
        OrderingStatementsDto statementsDto = OrderingStatementsDto.builder()
            .orderId(order_id)
            .build();
        List<OrderingStatementsDto> result = statementsSqlService.getOrderingStatementsListByPk(statementsDto);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // SELECT * (scan all) using SQL
    @GetMapping("/scan-all")
    public ResponseEntity<ApiResponse<List<OrderingStatementsDto>>> getOrderingStatementsListAll() {
        List<OrderingStatementsDto> result = statementsSqlService.getOrderingStatementsListAll();
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
