package com.example.demo_scalardb_samples.model;

import lombok.*;
import com.scalar.db.io.Key;
import java.time.*;
import java.nio.ByteBuffer;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderingStatements {

    public static final String NAMESPACE = "ordering";
    public static final String TABLE = "statements";
    public static final String ORDER_ID = "order_id";
    public static final String LINE_NO = "line_no";
    public static final String ITEM_ID = "item_id";
    public static final String QTY = "qty";
    public static final String UNIT_PRICE = "unit_price";

    private String orderId;
    private Integer lineNo;
    private Integer itemId;
    private Integer qty;
    private Long unitPrice;

    public Key getPartitionKey() {
        return Key.newBuilder().addText(ORDER_ID, getOrderId()).build();
    }

    public Key getClusteringKey() {
        return Key.newBuilder().addInt(LINE_NO, getLineNo()).build();
    }
}
