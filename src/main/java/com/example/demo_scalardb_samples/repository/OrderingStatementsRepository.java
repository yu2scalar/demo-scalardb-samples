package com.example.demo_scalardb_samples.repository;

import com.example.demo_scalardb_samples.exception.CustomException;
import com.example.demo_scalardb_samples.model.OrderingStatements;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.scalar.db.api.*;
import com.scalar.db.exception.transaction.*;
import com.scalar.db.io.Key;
import org.springframework.stereotype.Repository;

@Repository
public class OrderingStatementsRepository {

    private int scanLimit = 100; // Default scan limit
    
    public void setScanLimit(int scanLimit) {
        this.scanLimit = scanLimit;
    }
    
    public int getScanLimit() {
        return scanLimit;
    }

    // Get Record by Partition & Clustering Key
    public OrderingStatements getOrderingStatements(DistributedTransaction transaction, OrderingStatements statements) throws CrudException {
        Key partitionKey = statements.getPartitionKey();
        Key clusteringKey = statements.getClusteringKey();
        Get get = Get.newBuilder()
            .namespace(OrderingStatements.NAMESPACE)
            .table(OrderingStatements.TABLE)
            .partitionKey(partitionKey)
            
            .clusteringKey(clusteringKey)
            .projections(OrderingStatements.ORDER_ID, OrderingStatements.LINE_NO, OrderingStatements.ITEM_ID, OrderingStatements.QTY, OrderingStatements.UNIT_PRICE)
            .build();
        Optional<Result> result = transaction.get(get);
        if (result.isEmpty()) {
            throw new CustomException("No record found in OrderingStatements", 9700);
        }
        return buildOrderingStatements(result.get());
    }

    // Insert Record
    public OrderingStatements insertOrderingStatements(DistributedTransaction transaction, OrderingStatements statements) throws CrudException {
        Key partitionKey = statements.getPartitionKey();
        Key clusteringKey = statements.getClusteringKey();
        Insert insert = Insert.newBuilder()
            .namespace(OrderingStatements.NAMESPACE)
            .table(OrderingStatements.TABLE)
            .partitionKey(partitionKey)
            .clusteringKey(clusteringKey)
            .intValue(OrderingStatements.ITEM_ID, statements.getItemId())
            .intValue(OrderingStatements.QTY, statements.getQty())
            .bigIntValue(OrderingStatements.UNIT_PRICE, statements.getUnitPrice())
            .build();
        transaction.insert(insert);
        return statements;
    }

    // Update Record
    public OrderingStatements updateOrderingStatements(DistributedTransaction transaction, OrderingStatements statements) throws CrudException {
        Key partitionKey = statements.getPartitionKey();
        Key clusteringKey = statements.getClusteringKey();
        MutationCondition condition = ConditionBuilder.updateIfExists();

        Update update = Update.newBuilder()
            .namespace(OrderingStatements.NAMESPACE)
            .table(OrderingStatements.TABLE)
            .partitionKey(partitionKey)
            .clusteringKey(clusteringKey)
            .intValue(OrderingStatements.ITEM_ID, statements.getItemId())
            .intValue(OrderingStatements.QTY, statements.getQty())
            .bigIntValue(OrderingStatements.UNIT_PRICE, statements.getUnitPrice())
            .condition(condition)
            .build();
        transaction.update(update);
        return statements;
    }

    // Upsert Record
    public OrderingStatements upsertOrderingStatements(DistributedTransaction transaction, OrderingStatements statements) throws CrudException {
        Key partitionKey = statements.getPartitionKey();
        Key clusteringKey = statements.getClusteringKey();
        Upsert upsert = Upsert.newBuilder()
            .namespace(OrderingStatements.NAMESPACE)
            .table(OrderingStatements.TABLE)
            .partitionKey(partitionKey)
            .clusteringKey(clusteringKey)
            .intValue(OrderingStatements.ITEM_ID, statements.getItemId())
            .intValue(OrderingStatements.QTY, statements.getQty())
            .bigIntValue(OrderingStatements.UNIT_PRICE, statements.getUnitPrice())
            .build();
        transaction.upsert(upsert);
        return statements;
    }

    // Delete Record
    public void deleteOrderingStatements(DistributedTransaction transaction, OrderingStatements statements) throws CrudException {
        Key partitionKey = statements.getPartitionKey();
        Key clusteringKey = statements.getClusteringKey();
        MutationCondition condition = ConditionBuilder.deleteIfExists();
        Delete delete = Delete.newBuilder()
            .namespace(OrderingStatements.NAMESPACE)
            .table(OrderingStatements.TABLE)
            .partitionKey(partitionKey)
            
            .clusteringKey(clusteringKey)
            .condition(condition)
            .build();
        transaction.delete(delete);
    }

    // Scan All Records
    public List<OrderingStatements> getOrderingStatementsListAll(DistributedTransaction transaction) throws CrudException {
        Scan scan = Scan.newBuilder()
            .namespace(OrderingStatements.NAMESPACE)
            .table(OrderingStatements.TABLE)
            .all()
            .projections(OrderingStatements.ORDER_ID, OrderingStatements.LINE_NO, OrderingStatements.ITEM_ID, OrderingStatements.QTY, OrderingStatements.UNIT_PRICE)
            .limit(scanLimit)
            .build();
        List<Result> results = transaction.scan(scan);
        List<OrderingStatements> statementsList = new ArrayList<>();
        for (Result result : results) {
            statementsList.add(buildOrderingStatements(result));
        }
        return statementsList;
    }

    // Scan Records by Partition Key
    public List<OrderingStatements> getOrderingStatementsListByPk(DistributedTransaction transaction, Key partitionKey) throws CrudException {
        Scan scan = Scan.newBuilder()
            .namespace(OrderingStatements.NAMESPACE)
            .table(OrderingStatements.TABLE)
            .partitionKey(partitionKey)
            .projections(OrderingStatements.ORDER_ID, OrderingStatements.LINE_NO, OrderingStatements.ITEM_ID, OrderingStatements.QTY, OrderingStatements.UNIT_PRICE)
            .limit(scanLimit)
            .build();
        List<Result> results = transaction.scan(scan);
        List<OrderingStatements> statementsList = new ArrayList<>();
        for (Result result : results) {
            statementsList.add(buildOrderingStatements(result));
        }
        return statementsList;
    }

    // Object Builder from ScalarDB Result
    private OrderingStatements buildOrderingStatements(Result result) {
        return OrderingStatements.builder()
            .orderId(result.getText(OrderingStatements.ORDER_ID))
            .lineNo(result.getInt(OrderingStatements.LINE_NO))
            .itemId(result.getInt(OrderingStatements.ITEM_ID))
            .qty(result.getInt(OrderingStatements.QTY))
            .unitPrice(result.getBigInt(OrderingStatements.UNIT_PRICE))
            .build();
    }
}