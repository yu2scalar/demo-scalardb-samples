package com.example.demo_scalardb_samples.repository;

import com.example.demo_scalardb_samples.exception.CustomException;
import com.example.demo_scalardb_samples.model.InventoryItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.scalar.db.api.*;
import com.scalar.db.exception.transaction.*;
import com.scalar.db.io.Key;
import org.springframework.stereotype.Repository;

@Repository
public class InventoryItemsRepository {

    private int scanLimit = 100; // Default scan limit
    
    public void setScanLimit(int scanLimit) {
        this.scanLimit = scanLimit;
    }
    
    public int getScanLimit() {
        return scanLimit;
    }

    // Get Record by Partition & Clustering Key
    public InventoryItems getInventoryItems(DistributedTransaction transaction, InventoryItems items) throws CrudException {
        Key partitionKey = items.getPartitionKey();
        
        Get get = Get.newBuilder()
            .namespace(InventoryItems.NAMESPACE)
            .table(InventoryItems.TABLE)
            .partitionKey(partitionKey)
            
            .projections(InventoryItems.ITEM_ID, InventoryItems.NAME, InventoryItems.PRICE, InventoryItems.STOCK)
            .build();
        Optional<Result> result = transaction.get(get);
        if (result.isEmpty()) {
            throw new CustomException("No record found in InventoryItems", 9700);
        }
        return buildInventoryItems(result.get());
    }

    // Insert Record
    public InventoryItems insertInventoryItems(DistributedTransaction transaction, InventoryItems items) throws CrudException {
        Key partitionKey = items.getPartitionKey();
        
        Insert insert = Insert.newBuilder()
            .namespace(InventoryItems.NAMESPACE)
            .table(InventoryItems.TABLE)
            .partitionKey(partitionKey)
            .textValue(InventoryItems.NAME, items.getName())
            .bigIntValue(InventoryItems.PRICE, items.getPrice())
            .intValue(InventoryItems.STOCK, items.getStock())
            .build();
        transaction.insert(insert);
        return items;
    }

    // Update Record
    public InventoryItems updateInventoryItems(DistributedTransaction transaction, InventoryItems items) throws CrudException {
        Key partitionKey = items.getPartitionKey();
        
        MutationCondition condition = ConditionBuilder.updateIfExists();

        Update update = Update.newBuilder()
            .namespace(InventoryItems.NAMESPACE)
            .table(InventoryItems.TABLE)
            .partitionKey(partitionKey)
            .textValue(InventoryItems.NAME, items.getName())
            .bigIntValue(InventoryItems.PRICE, items.getPrice())
            .intValue(InventoryItems.STOCK, items.getStock())
            .condition(condition)
            .build();
        transaction.update(update);
        return items;
    }

    // Upsert Record
    public InventoryItems upsertInventoryItems(DistributedTransaction transaction, InventoryItems items) throws CrudException {
        Key partitionKey = items.getPartitionKey();
        
        Upsert upsert = Upsert.newBuilder()
            .namespace(InventoryItems.NAMESPACE)
            .table(InventoryItems.TABLE)
            .partitionKey(partitionKey)
            .textValue(InventoryItems.NAME, items.getName())
            .bigIntValue(InventoryItems.PRICE, items.getPrice())
            .intValue(InventoryItems.STOCK, items.getStock())
            .build();
        transaction.upsert(upsert);
        return items;
    }

    // Delete Record
    public void deleteInventoryItems(DistributedTransaction transaction, InventoryItems items) throws CrudException {
        Key partitionKey = items.getPartitionKey();
        
        MutationCondition condition = ConditionBuilder.deleteIfExists();
        Delete delete = Delete.newBuilder()
            .namespace(InventoryItems.NAMESPACE)
            .table(InventoryItems.TABLE)
            .partitionKey(partitionKey)
            
            .condition(condition)
            .build();
        transaction.delete(delete);
    }

    // Scan All Records
    public List<InventoryItems> getInventoryItemsListAll(DistributedTransaction transaction) throws CrudException {
        Scan scan = Scan.newBuilder()
            .namespace(InventoryItems.NAMESPACE)
            .table(InventoryItems.TABLE)
            .all()
            .projections(InventoryItems.ITEM_ID, InventoryItems.NAME, InventoryItems.PRICE, InventoryItems.STOCK)
            .limit(scanLimit)
            .build();
        List<Result> results = transaction.scan(scan);
        List<InventoryItems> itemsList = new ArrayList<>();
        for (Result result : results) {
            itemsList.add(buildInventoryItems(result));
        }
        return itemsList;
    }

    // Scan Records by Partition Key
    public List<InventoryItems> getInventoryItemsListByPk(DistributedTransaction transaction, Key partitionKey) throws CrudException {
        Scan scan = Scan.newBuilder()
            .namespace(InventoryItems.NAMESPACE)
            .table(InventoryItems.TABLE)
            .partitionKey(partitionKey)
            .projections(InventoryItems.ITEM_ID, InventoryItems.NAME, InventoryItems.PRICE, InventoryItems.STOCK)
            .limit(scanLimit)
            .build();
        List<Result> results = transaction.scan(scan);
        List<InventoryItems> itemsList = new ArrayList<>();
        for (Result result : results) {
            itemsList.add(buildInventoryItems(result));
        }
        return itemsList;
    }

    // Object Builder from ScalarDB Result
    private InventoryItems buildInventoryItems(Result result) {
        return InventoryItems.builder()
            .itemId(result.getInt(InventoryItems.ITEM_ID))
            .name(result.getText(InventoryItems.NAME))
            .price(result.getBigInt(InventoryItems.PRICE))
            .stock(result.getInt(InventoryItems.STOCK))
            .build();
    }
}