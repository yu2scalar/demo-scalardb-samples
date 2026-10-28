package com.example.demo_scalardb_samples.controller;

import com.example.demo_scalardb_samples.service.GenericSqlService;
import com.example.demo_scalardb_samples.dto.ApiResponse;
import com.example.demo_scalardb_samples.dto.ResponseStatusDto;
import com.example.demo_scalardb_samples.dto.SqlCommandDto;
import com.example.demo_scalardb_samples.exception.CustomException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.List;
import java.util.Map;

@RequestMapping(value = "/generic-sql")
@RestController
public class GenericSqlController {
    @Autowired
    private GenericSqlService genericSqlService;

    /**
     * Generic SQL execution endpoint (SQL-console-like).
     *
     * WARNING: SAMPLE ONLY — do NOT copy this pattern to production.
     * This path executes client-supplied SQL verbatim, with none of the
     * injection countermeasures the typed repositories (SqlRepository) apply.
     * Real applications should expose only fixed statements via the typed path.
     */
    @PostMapping("/execute")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> executeSQLGeneric(@RequestBody SqlCommandDto sqlCommandDto) {
        List<Map<String, Object>> result = genericSqlService.executeSQLGeneric(sqlCommandDto);
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
