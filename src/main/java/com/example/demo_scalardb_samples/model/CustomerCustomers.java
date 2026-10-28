package com.example.demo_scalardb_samples.model;

import lombok.*;
import com.scalar.db.io.Key;
import java.time.*;
import java.nio.ByteBuffer;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerCustomers {

    public static final String NAMESPACE = "customer";
    public static final String TABLE = "customers";
    public static final String CUSTOMER_ID = "customer_id";
    public static final String NAME = "name";
    public static final String CREDIT_LIMIT = "credit_limit";
    public static final String CREDIT_TOTAL = "credit_total";

    private Integer customerId;
    private String name;
    private Long creditLimit;
    private Long creditTotal;

    public Key getPartitionKey() {
        return Key.newBuilder().addInt(CUSTOMER_ID, getCustomerId()).build();
    }

}
