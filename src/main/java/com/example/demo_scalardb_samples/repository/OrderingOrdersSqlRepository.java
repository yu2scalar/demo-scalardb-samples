package com.example.demo_scalardb_samples.repository;

import com.example.demo_scalardb_samples.model.OrderingOrders;
import com.scalar.db.io.Key;
import com.scalar.db.io.Value;
import com.scalar.db.sql.SqlSession;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

/**
 * SQL Repository for OrderingOrders.
 *
 * Inherits from SqlRepository base class:
 * - insert(session, model)
 * - upsert(session, model)
 * - get(session, model)
 * - update(session, model)
 * - delete(session, model)
 * - executeQuery(session, sql)
 *
 * Protected utilities available for custom queries:
 * - buildSelectFrom() - returns "SELECT * FROM namespace.table"
 * - formatValue(value, type) - formats value for SQL with sanitization
 * - quoteIdentifier(name) - quotes reserved keywords
 * - fixTimestampTzFormat(sql) - fixes timestamp format issues
 */
@Repository
public class OrderingOrdersSqlRepository extends SqlRepository<OrderingOrders> {

    /** Default limit for scan operations to prevent memory issues */
    private static final int DEFAULT_SCAN_LIMIT = 1000;

    public OrderingOrdersSqlRepository() {
        super(OrderingOrders.class);
    }

    // ==================== SCAN OPERATIONS ====================

    /**
     * Scan all records.
     * Transaction must be started before calling this method.
     *
     * @param session Active SQL session with transaction started
     * @return List of all entities (limited to DEFAULT_SCAN_LIMIT)
     * @throws Exception if scan fails
     */
    public List<OrderingOrders> scan(SqlSession session) throws Exception {
        String sql = buildSelectFrom() + " LIMIT " + DEFAULT_SCAN_LIMIT;
        return executeQuery(session, fixTimestampTzFormat(sql));
    }

    /**
     * Scan records by partition key.
     * Transaction must be started before calling this method.
     *
     * @param session Active SQL session with transaction started
     * @param model Model/Entity containing partition key values
     * @return List of matching entities (limited to DEFAULT_SCAN_LIMIT)
     * @throws Exception if scan fails
     */
    public List<OrderingOrders> scan(SqlSession session, OrderingOrders model) throws Exception {
        Key partitionKey = model.getPartitionKey();

        StringBuilder sql = new StringBuilder();
        sql.append(buildSelectFrom());
        sql.append(" WHERE ");

        List<String> conditions = new ArrayList<>();

        for (Value<?> column : partitionKey.get()) {
            String columnName = column.getName();
            Object value = column.get();
            // Unwrap Optional if present
            if (value instanceof java.util.Optional) {
                value = ((java.util.Optional<?>) value).orElseThrow(() ->
                    new IllegalArgumentException("Key value cannot be null for column: " + columnName));
            }
            conditions.add(quoteIdentifier(columnName) + " = " + formatValueByObject(value));
        }

        if (conditions.isEmpty()) {
            throw new IllegalArgumentException("No valid partition key values provided");
        }

        sql.append(String.join(" AND ", conditions));
        sql.append(" LIMIT ").append(DEFAULT_SCAN_LIMIT);

        return executeQuery(session, fixTimestampTzFormat(sql.toString()));
    }

    /**
     * Scan records by customer_id. No limit: all orders of the customer.
     * Whether the index on customer_id is used is decided by ScalarDB SQL.
     * Transaction must be started before calling this method.
     *
     * @param session Active SQL session with transaction started
     * @param customerId Customer ID
     * @return List of matching entities
     * @throws Exception if scan fails
     */
    public List<OrderingOrders> scanByCustomerId(SqlSession session, int customerId) throws Exception {
        String sql = buildSelectFrom() + " WHERE " + quoteIdentifier(OrderingOrders.CUSTOMER_ID) + " = " + customerId;
        return executeQuery(session, sql);
    }
}
