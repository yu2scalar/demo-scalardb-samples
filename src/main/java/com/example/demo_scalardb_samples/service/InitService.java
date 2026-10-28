package com.example.demo_scalardb_samples.service;

import com.example.demo_scalardb_samples.exception.CustomException;
import com.example.demo_scalardb_samples.model.CustomerCustomers;
import com.example.demo_scalardb_samples.model.InventoryItems;
import com.example.demo_scalardb_samples.model.OrderingOrders;
import com.example.demo_scalardb_samples.model.OrderingStatements;
import com.example.demo_scalardb_samples.repository.CustomerCustomersRepository;
import com.example.demo_scalardb_samples.repository.InventoryItemsRepository;
import com.scalar.db.api.DistributedTransaction;
import com.scalar.db.api.DistributedTransactionAdmin;
import com.scalar.db.api.DistributedTransactionManager;
import com.scalar.db.exception.storage.ExecutionException;
import com.scalar.db.exception.transaction.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.retry.RetryException;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.stereotype.Service;

/**
 * Initial data for the business logic (LoadInitialData of scalardb-sample), with the
 * number of rows given by the caller. Keys are 1..count; the other columns are fixed
 * values. Existing rows are overwritten (upsert). Rows are written BATCH_SIZE per
 * transaction. truncateAll() empties the four tables beforehand (POST /init?truncate=true).
 */
@Slf4j
@Service
public class InitService {
    public static final int BATCH_SIZE = 100;
    public static final long CREDIT_LIMIT = 1_000_000L;
    public static final long PRICE = 100L;
    public static final int STOCK = 1_000_000;

    // Children first; the order has no effect since truncation is not transactional
    private static final String[][] TRUNCATE_TABLES = {
        {OrderingStatements.NAMESPACE, OrderingStatements.TABLE},
        {OrderingOrders.NAMESPACE, OrderingOrders.TABLE},
        {CustomerCustomers.NAMESPACE, CustomerCustomers.TABLE},
        {InventoryItems.NAMESPACE, InventoryItems.TABLE},
    };

    DistributedTransactionManager manager;

    @Autowired
    DistributedTransactionAdmin admin;

    @Autowired
    @Qualifier("coreApiRetryTemplate")
    private RetryTemplate coreApiRetryTemplate;

    @Autowired
    CustomerCustomersRepository customersRepository;

    @Autowired
    InventoryItemsRepository itemsRepository;

    public InitService(DistributedTransactionManager manager) {
        this.manager = manager;
    }

    // Empties the four tables with Admin.truncateTable. Not transactional: run it
    // while no other transactions are running. coordinator.state is left as is.
    public void truncateAll() throws CustomException {
        for (String[] t : TRUNCATE_TABLES) {
            String name = t[0] + "." + t[1];
            log.info("Truncating {}", name);
            try {
                admin.truncateTable(t[0], t[1]);
            } catch (ExecutionException e) {
                log.error(e.getMessage(), e);
                throw new CustomException("Failed to truncate " + name + ": " + e.getMessage(), e, 9500);
            }
            log.info("Truncated {}", name);
        }
    }

    // customer_id 1..count, credit_limit 1,000,000, credit_total 0
    public int initCustomers(int count) throws CustomException {
        return initCustomers(count, CREDIT_LIMIT);
    }

    // As above with the given credit_limit (the load test uses a large one)
    public int initCustomers(int count, long creditLimit) throws CustomException {
        checkCount(count);
        for (int from = 1; from <= count; from += BATCH_SIZE) {
            int start = from;
            int end = Math.min(from + BATCH_SIZE - 1, count);
            runInTransaction(transaction -> {
                for (int id = start; id <= end; id++) {
                    customersRepository.upsertCustomerCustomers(transaction, CustomerCustomers.builder()
                        .customerId(id)
                        .name(OrderRules.testName(id))
                        .creditLimit(creditLimit)
                        .creditTotal(0L)
                        .build());
                }
            });
        }
        return count;
    }

    // item_id 1..count, price 100, stock 1,000,000
    public int initItems(int count) throws CustomException {
        return initItems(count, STOCK);
    }

    // As above with the given stock (the load test uses a large one)
    public int initItems(int count, int stock) throws CustomException {
        checkCount(count);
        for (int from = 1; from <= count; from += BATCH_SIZE) {
            int start = from;
            int end = Math.min(from + BATCH_SIZE - 1, count);
            runInTransaction(transaction -> {
                for (int id = start; id <= end; id++) {
                    itemsRepository.upsertInventoryItems(transaction, InventoryItems.builder()
                        .itemId(id)
                        .name(OrderRules.testName(id))
                        .price(PRICE)
                        .stock(stock)
                        .build());
                }
            });
        }
        return count;
    }

    private static void checkCount(int count) {
        if (count < 0) {
            throw new CustomException("count must not be negative: " + count, OrderRules.INVALID_REQUEST);
        }
    }

    @FunctionalInterface
    private interface TransactionWork {
        void run(DistributedTransaction transaction) throws Exception;
    }

    // One transaction with retry, as in the table services
    private void runInTransaction(TransactionWork work) throws CustomException {
        try {
            coreApiRetryTemplate.execute(() -> {
                DistributedTransaction transaction = null;
                try {
                    transaction = manager.start();
                    work.run(transaction);
                    transaction.commit();
                    return null;
                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                    if (transaction != null && !(e instanceof UnknownTransactionStatusException)) {
                        try {
                            transaction.rollback();
                        } catch (Exception ex) {
                            log.error(ex.getMessage(), ex);
                        }
                    }
                    throw e;
                }
            });
        } catch (RetryException re) {
            Throwable cause = (re.getCause() != null) ? re.getCause() : re;
            int code = (cause instanceof UnknownTransactionStatusException) ? 9200
                : (cause instanceof TransactionException) ? 9300 : 9500;
            throw new CustomException(cause.getMessage(), cause, code);
        }
    }
}
