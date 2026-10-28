package com.example.demo_scalardb_samples.controller;

import com.example.demo_scalardb_samples.service.InventoryItemsSqlService;
import com.example.demo_scalardb_samples.dto.InventoryItemsDto;
import com.example.demo_scalardb_samples.dto.ApiResponse;
import com.example.demo_scalardb_samples.dto.ResponseStatusDto;
import com.example.demo_scalardb_samples.exception.CustomException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

@RequestMapping(value = "/inventory-items-sql")
@RestController
public class InventoryItemsSqlController {
    @Autowired
    private InventoryItemsSqlService itemsSqlService;

    // INSERT using SQL
    @PostMapping
    public ResponseEntity<ApiResponse<Void>> insertInventoryItems(@RequestBody InventoryItemsDto itemsDto) {
        ResponseStatusDto status = itemsSqlService.insertInventoryItems(itemsDto);
        return ResponseEntity.ok(ApiResponse.fromResponseStatus(status));
    }

    // UPSERT using SQL (insert or update if exists)
    @PostMapping("/upsert")
    public ResponseEntity<ApiResponse<Void>> upsertInventoryItems(@RequestBody InventoryItemsDto itemsDto) {
        ResponseStatusDto status = itemsSqlService.upsertInventoryItems(itemsDto);
        return ResponseEntity.ok(ApiResponse.fromResponseStatus(status));
    }

    // SELECT using SQL (get by key)
    @GetMapping("/{item_id}")
    public ResponseEntity<ApiResponse<InventoryItemsDto>> getInventoryItems(@PathVariable("item_id") Integer item_id) {
        InventoryItemsDto itemsDto = InventoryItemsDto.builder()
            .itemId(item_id)
            .build();
        InventoryItemsDto result = itemsSqlService.getInventoryItems(itemsDto);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // UPDATE using SQL
    @PutMapping
    public ResponseEntity<ApiResponse<Void>> updateInventoryItems(@RequestBody InventoryItemsDto itemsDto) {
        ResponseStatusDto status = itemsSqlService.updateInventoryItems(itemsDto);
        return ResponseEntity.ok(ApiResponse.fromResponseStatus(status));
    }

    // DELETE using SQL
    @DeleteMapping("/{item_id}")
    public ResponseEntity<ApiResponse<Void>> deleteInventoryItems(@PathVariable("item_id") Integer item_id) {
        InventoryItemsDto itemsDto = InventoryItemsDto.builder()
            .itemId(item_id)
            .build();
        ResponseStatusDto status = itemsSqlService.deleteInventoryItems(itemsDto);
        return ResponseEntity.ok(ApiResponse.fromResponseStatus(status));
    }

    // SELECT with partition key using SQL (returns List)
    @GetMapping("/scan-by-pk/{item_id}")
    public ResponseEntity<ApiResponse<List<InventoryItemsDto>>> getInventoryItemsByPk(@PathVariable("item_id") Integer item_id) {
        InventoryItemsDto itemsDto = InventoryItemsDto.builder()
            .itemId(item_id)
            .build();
        List<InventoryItemsDto> result = itemsSqlService.getInventoryItemsListByPk(itemsDto);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // SELECT * (scan all) using SQL
    @GetMapping("/scan-all")
    public ResponseEntity<ApiResponse<List<InventoryItemsDto>>> getInventoryItemsListAll() {
        List<InventoryItemsDto> result = itemsSqlService.getInventoryItemsListAll();
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
