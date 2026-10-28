package com.example.demo_scalardb_samples.repository;

import com.example.demo_scalardb_samples.exception.CustomException;
import com.example.demo_scalardb_samples.model.OrderingOrders;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.scalar.db.api.*;
import com.scalar.db.exception.transaction.*;
import com.scalar.db.io.Key;
import org.springframework.stereotype.Repository;

@Repository
public class OrderingOrdersRepository {

    private int scanLimit = 100; // Default scan limit
    
    public void setScanLimit(int scanLimit) {
        this.scanLimit = scanLimit;
    }
    
    public int getScanLimit() {
        return scanLimit;
    }

    // Get Record by Partition & Clustering Key
    public OrderingOrders getOrderingOrders(DistributedTransaction transaction, OrderingOrders orders) throws CrudException {
        Key partitionKey = orders.getPartitionKey();
        
        Get get = Get.newBuilder()
            .namespace(OrderingOrders.NAMESPACE)
            .table(OrderingOrders.TABLE)
            .partitionKey(partitionKey)
            
            .projections(OrderingOrders.ORDER_ID, OrderingOrders.CUSTOMER_ID, OrderingOrders.CREATED_AT, OrderingOrders.STATUS, OrderingOrders.TOTAL_AMOUNT)
            .build();
        Optional<Result> result = transaction.get(get);
        if (result.isEmpty()) {
            throw new CustomException("No record found in OrderingOrders", 9700);
        }
        return buildOrderingOrders(result.get());
    }

    // Insert Record
    public OrderingOrders insertOrderingOrders(DistributedTransaction transaction, OrderingOrders orders) throws CrudException {
        Key partitionKey = orders.getPartitionKey();
        
        Insert insert = Insert.newBuilder()
            .namespace(OrderingOrders.NAMESPACE)
            .table(OrderingOrders.TABLE)
            .partitionKey(partitionKey)
            .intValue(OrderingOrders.CUSTOMER_ID, orders.getCustomerId())
            .bigIntValue(OrderingOrders.CREATED_AT, orders.getCreatedAt())
            .textValue(OrderingOrders.STATUS, orders.getStatus())
            .bigIntValue(OrderingOrders.TOTAL_AMOUNT, orders.getTotalAmount())
            .build();
        transaction.insert(insert);
        return orders;
    }

    // Update Record
    public OrderingOrders updateOrderingOrders(DistributedTransaction transaction, OrderingOrders orders) throws CrudException {
        Key partitionKey = orders.getPartitionKey();
        
        MutationCondition condition = ConditionBuilder.updateIfExists();

        Update update = Update.newBuilder()
            .namespace(OrderingOrders.NAMESPACE)
            .table(OrderingOrders.TABLE)
            .partitionKey(partitionKey)
            .intValue(OrderingOrders.CUSTOMER_ID, orders.getCustomerId())
            .bigIntValue(OrderingOrders.CREATED_AT, orders.getCreatedAt())
            .textValue(OrderingOrders.STATUS, orders.getStatus())
            .bigIntValue(OrderingOrders.TOTAL_AMOUNT, orders.getTotalAmount())
            .condition(condition)
            .build();
        transaction.update(update);
        return orders;
    }

    // Upsert Record
    public OrderingOrders upsertOrderingOrders(DistributedTransaction transaction, OrderingOrders orders) throws CrudException {
        Key partitionKey = orders.getPartitionKey();
        
        Upsert upsert = Upsert.newBuilder()
            .namespace(OrderingOrders.NAMESPACE)
            .table(OrderingOrders.TABLE)
            .partitionKey(partitionKey)
            .intValue(OrderingOrders.CUSTOMER_ID, orders.getCustomerId())
            .bigIntValue(OrderingOrders.CREATED_AT, orders.getCreatedAt())
            .textValue(OrderingOrders.STATUS, orders.getStatus())
            .bigIntValue(OrderingOrders.TOTAL_AMOUNT, orders.getTotalAmount())
            .build();
        transaction.upsert(upsert);
        return orders;
    }

    // Delete Record
    public void deleteOrderingOrders(DistributedTransaction transaction, OrderingOrders orders) throws CrudException {
        Key partitionKey = orders.getPartitionKey();
        
        MutationCondition condition = ConditionBuilder.deleteIfExists();
        Delete delete = Delete.newBuilder()
            .namespace(OrderingOrders.NAMESPACE)
            .table(OrderingOrders.TABLE)
            .partitionKey(partitionKey)
            
            .condition(condition)
            .build();
        transaction.delete(delete);
    }

    // Scan All Records
    public List<OrderingOrders> getOrderingOrdersListAll(DistributedTransaction transaction) throws CrudException {
        Scan scan = Scan.newBuilder()
            .namespace(OrderingOrders.NAMESPACE)
            .table(OrderingOrders.TABLE)
            .all()
            .projections(OrderingOrders.ORDER_ID, OrderingOrders.CUSTOMER_ID, OrderingOrders.CREATED_AT, OrderingOrders.STATUS, OrderingOrders.TOTAL_AMOUNT)
            .limit(scanLimit)
            .build();
        List<Result> results = transaction.scan(scan);
        List<OrderingOrders> ordersList = new ArrayList<>();
        for (Result result : results) {
            ordersList.add(buildOrderingOrders(result));
        }
        return ordersList;
    }

    // Scan Records by Partition Key
    public List<OrderingOrders> getOrderingOrdersListByPk(DistributedTransaction transaction, Key partitionKey) throws CrudException {
        Scan scan = Scan.newBuilder()
            .namespace(OrderingOrders.NAMESPACE)
            .table(OrderingOrders.TABLE)
            .partitionKey(partitionKey)
            .projections(OrderingOrders.ORDER_ID, OrderingOrders.CUSTOMER_ID, OrderingOrders.CREATED_AT, OrderingOrders.STATUS, OrderingOrders.TOTAL_AMOUNT)
            .limit(scanLimit)
            .build();
        List<Result> results = transaction.scan(scan);
        List<OrderingOrders> ordersList = new ArrayList<>();
        for (Result result : results) {
            ordersList.add(buildOrderingOrders(result));
        }
        return ordersList;
    }

    // Scan Records by Secondary Index (customer_id). No limit: all orders of the customer.
    public List<OrderingOrders> getOrderingOrdersListByCustomerIdIndex(DistributedTransaction transaction, int customerId) throws CrudException {
        Scan scan = Scan.newBuilder()
            .namespace(OrderingOrders.NAMESPACE)
            .table(OrderingOrders.TABLE)
            .indexKey(Key.ofInt(OrderingOrders.CUSTOMER_ID, customerId))
            .projections(OrderingOrders.ORDER_ID, OrderingOrders.CUSTOMER_ID, OrderingOrders.CREATED_AT, OrderingOrders.STATUS, OrderingOrders.TOTAL_AMOUNT)
            .build();
        List<Result> results = transaction.scan(scan);
        List<OrderingOrders> ordersList = new ArrayList<>();
        for (Result result : results) {
            ordersList.add(buildOrderingOrders(result));
        }
        return ordersList;
    }

    // Scan All Records with where (customer_id). No limit: all orders of the customer.
    // Needs scalar.db.cross_partition_scan.enabled and .filtering.enabled.
    public List<OrderingOrders> getOrderingOrdersListByCustomerIdScan(DistributedTransaction transaction, int customerId) throws CrudException {
        Scan scan = Scan.newBuilder()
            .namespace(OrderingOrders.NAMESPACE)
            .table(OrderingOrders.TABLE)
            .all()
            .where(ConditionBuilder.column(OrderingOrders.CUSTOMER_ID).isEqualToInt(customerId))
            .projections(OrderingOrders.ORDER_ID, OrderingOrders.CUSTOMER_ID, OrderingOrders.CREATED_AT, OrderingOrders.STATUS, OrderingOrders.TOTAL_AMOUNT)
            .build();
        List<Result> results = transaction.scan(scan);
        List<OrderingOrders> ordersList = new ArrayList<>();
        for (Result result : results) {
            ordersList.add(buildOrderingOrders(result));
        }
        return ordersList;
    }

    // Object Builder from ScalarDB Result
    private OrderingOrders buildOrderingOrders(Result result) {
        return OrderingOrders.builder()
            .orderId(result.getText(OrderingOrders.ORDER_ID))
            .customerId(result.getInt(OrderingOrders.CUSTOMER_ID))
            .createdAt(result.getBigInt(OrderingOrders.CREATED_AT))
            .status(result.getText(OrderingOrders.STATUS))
            .totalAmount(result.getBigInt(OrderingOrders.TOTAL_AMOUNT))
            .build();
    }
}