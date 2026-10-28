package com.example.demo_scalardb_samples.model;

import lombok.*;
import com.scalar.db.io.Key;
import java.time.*;
import java.nio.ByteBuffer;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryItems {

    public static final String NAMESPACE = "inventory";
    public static final String TABLE = "items";
    public static final String ITEM_ID = "item_id";
    public static final String NAME = "name";
    public static final String PRICE = "price";
    public static final String STOCK = "stock";

    private Integer itemId;
    private String name;
    private Long price;
    private Integer stock;

    public Key getPartitionKey() {
        return Key.newBuilder().addInt(ITEM_ID, getItemId()).build();
    }

}
