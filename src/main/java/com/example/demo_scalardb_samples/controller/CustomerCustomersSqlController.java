package com.example.demo_scalardb_samples.controller;

import com.example.demo_scalardb_samples.service.CustomerCustomersSqlService;
import com.example.demo_scalardb_samples.dto.CustomerCustomersDto;
import com.example.demo_scalardb_samples.dto.ApiResponse;
import com.example.demo_scalardb_samples.dto.ResponseStatusDto;
import com.example.demo_scalardb_samples.exception.CustomException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

@RequestMapping(value = "/customer-customers-sql")
@RestController
public class CustomerCustomersSqlController {
    @Autowired
    private CustomerCustomersSqlService customersSqlService;

    // INSERT using SQL
    @PostMapping
    public ResponseEntity<ApiResponse<Void>> insertCustomerCustomers(@RequestBody CustomerCustomersDto customersDto) {
        ResponseStatusDto status = customersSqlService.insertCustomerCustomers(customersDto);
        return ResponseEntity.ok(ApiResponse.fromResponseStatus(status));
    }

    // UPSERT using SQL (insert or update if exists)
    @PostMapping("/upsert")
    public ResponseEntity<ApiResponse<Void>> upsertCustomerCustomers(@RequestBody CustomerCustomersDto customersDto) {
        ResponseStatusDto status = customersSqlService.upsertCustomerCustomers(customersDto);
        return ResponseEntity.ok(ApiResponse.fromResponseStatus(status));
    }

    // SELECT using SQL (get by key)
    @GetMapping("/{customer_id}")
    public ResponseEntity<ApiResponse<CustomerCustomersDto>> getCustomerCustomers(@PathVariable("customer_id") Integer customer_id) {
        CustomerCustomersDto customersDto = CustomerCustomersDto.builder()
            .customerId(customer_id)
            .build();
        CustomerCustomersDto result = customersSqlService.getCustomerCustomers(customersDto);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // UPDATE using SQL
    @PutMapping
    public ResponseEntity<ApiResponse<Void>> updateCustomerCustomers(@RequestBody CustomerCustomersDto customersDto) {
        ResponseStatusDto status = customersSqlService.updateCustomerCustomers(customersDto);
        return ResponseEntity.ok(ApiResponse.fromResponseStatus(status));
    }

    // DELETE using SQL
    @DeleteMapping("/{customer_id}")
    public ResponseEntity<ApiResponse<Void>> deleteCustomerCustomers(@PathVariable("customer_id") Integer customer_id) {
        CustomerCustomersDto customersDto = CustomerCustomersDto.builder()
            .customerId(customer_id)
            .build();
        ResponseStatusDto status = customersSqlService.deleteCustomerCustomers(customersDto);
        return ResponseEntity.ok(ApiResponse.fromResponseStatus(status));
    }

    // SELECT with partition key using SQL (returns List)
    @GetMapping("/scan-by-pk/{customer_id}")
    public ResponseEntity<ApiResponse<List<CustomerCustomersDto>>> getCustomerCustomersByPk(@PathVariable("customer_id") Integer customer_id) {
        CustomerCustomersDto customersDto = CustomerCustomersDto.builder()
            .customerId(customer_id)
            .build();
        List<CustomerCustomersDto> result = customersSqlService.getCustomerCustomersListByPk(customersDto);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // SELECT * (scan all) using SQL
    @GetMapping("/scan-all")
    public ResponseEntity<ApiResponse<List<CustomerCustomersDto>>> getCustomerCustomersListAll() {
        List<CustomerCustomersDto> result = customersSqlService.getCustomerCustomersListAll();
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
