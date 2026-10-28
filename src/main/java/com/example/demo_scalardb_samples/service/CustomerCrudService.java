package com.example.demo_scalardb_samples.service;

import com.example.demo_scalardb_samples.exception.CustomException;
import com.example.demo_scalardb_samples.model.CustomerCustomers;
import com.example.demo_scalardb_samples.repository.CustomerCustomersRepository;
import com.scalar.db.api.ConditionBuilder;
import com.scalar.db.api.ConditionalExpression;
import com.scalar.db.api.DistributedTransaction;
import com.scalar.db.api.DistributedTransactionManager;
import com.scalar.db.api.Result;
import com.scalar.db.api.Scan;
import com.scalar.db.exception.transaction.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntUnaryOperator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.retry.RetryException;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.stereotype.Service;

/**
 * Customers by name, plus read-modify-write and insert, for the customer-crud load test.
 *
 * The search is a ScanAll with a where condition on name (Scan.newBuilder().all().where(...)),
 * not a Get with the secondary index:
 * - exact:  name = value
 * - prefix: name LIKE 'value%'
 * Consensus Commit adds the same condition on before_name (OR), so that PREPARED records
 * whose before-image matches are not missed. Cross-partition scan with filtering must be
 * enabled. Retry and error handling follow OrderService.
 */
@Slf4j
@Service
public class CustomerCrudService {
    public static final String EXACT = "exact";
    public static final String PREFIX = "prefix";

    DistributedTransactionManager manager;

    // Transaction attempts including retries; the load test reads the difference
    private final AtomicLong attemptCount = new AtomicLong();

    @Autowired
    @Qualifier("coreApiRetryTemplate")
    private RetryTemplate coreApiRetryTemplate;

    @Autowired
    CustomerCustomersRepository customersRepository;

    public CustomerCrudService(DistributedTransactionManager manager) {
        this.manager = manager;
    }

    // Search by name in one transaction. limit 0 = no limit
    public List<CustomerCustomers> search(String value, String match, int limit) throws CustomException {
        Scan scan = searchScan(value, match, limit);
        return runInTransaction(transaction -> toCustomers(transaction.scan(scan)));
    }

    /**
     * Search by name, then add 1 to credit_total of one of the rows found, in one transaction.
     * chooser picks the index of the row to update from the number of rows found.
     * Returns the number of rows updated (0 if nothing was found).
     */
    public int readModifyWrite(String value, String match, int limit, IntUnaryOperator chooser)
            throws CustomException {
        Scan scan = searchScan(value, match, limit);
        return runInTransaction(transaction -> {
            List<CustomerCustomers> found = toCustomers(transaction.scan(scan));
            if (found.isEmpty()) {
                return 0;
            }
            CustomerCustomers customer = found.get(chooser.applyAsInt(found.size()));
            customer.setCreditTotal(customer.getCreditTotal() + 1);
            customersRepository.updateCustomerCustomers(transaction, customer);
            return 1;
        });
    }

    // Insert customer_id from..to in one transaction (name by OrderRules.testName, credit_total 0)
    public int insertRange(int from, int to, long creditLimit) throws CustomException {
        return runInTransaction(transaction -> {
            for (int id = from; id <= to; id++) {
                customersRepository.insertCustomerCustomers(transaction, CustomerCustomers.builder()
                    .customerId(id)
                    .name(OrderRules.testName(id))
                    .creditLimit(creditLimit)
                    .creditTotal(0L)
                    .build());
            }
            return to - from + 1;
        });
    }

    public long getAttemptCount() {
        return attemptCount.get();
    }

    private static Scan searchScan(String value, String match, int limit) {
        if (value == null || value.isEmpty()) {
            throw new CustomException("name must not be empty", OrderRules.INVALID_REQUEST);
        }
        ConditionalExpression condition;
        if (EXACT.equals(match)) {
            condition = ConditionBuilder.column(CustomerCustomers.NAME).isEqualToText(value);
        } else if (PREFIX.equals(match)) {
            condition = ConditionBuilder.column(CustomerCustomers.NAME).isLikeText(value + "%");
        } else {
            throw new CustomException("match must be exact or prefix: " + match, OrderRules.INVALID_REQUEST);
        }
        return Scan.newBuilder()
            .namespace(CustomerCustomers.NAMESPACE)
            .table(CustomerCustomers.TABLE)
            .all()
            .where(condition)
            .projections(CustomerCustomers.CUSTOMER_ID, CustomerCustomers.NAME,
                CustomerCustomers.CREDIT_LIMIT, CustomerCustomers.CREDIT_TOTAL)
            .limit(limit)
            .build();
    }

    private static List<CustomerCustomers> toCustomers(List<Result> results) {
        List<CustomerCustomers> customers = new ArrayList<>(results.size());
        for (Result r : results) {
            customers.add(CustomerCustomers.builder()
                .customerId(r.getInt(CustomerCustomers.CUSTOMER_ID))
                .name(r.getText(CustomerCustomers.NAME))
                .creditLimit(r.getBigInt(CustomerCustomers.CREDIT_LIMIT))
                .creditTotal(r.getBigInt(CustomerCustomers.CREDIT_TOTAL))
                .build());
        }
        return customers;
    }

    @FunctionalInterface
    private interface TransactionWork<T> {
        T run(DistributedTransaction transaction) throws Exception;
    }

    // One transaction with retry, as in OrderService
    private <T> T runInTransaction(TransactionWork<T> work) throws CustomException {
        try {
            return coreApiRetryTemplate.execute(() -> {
                attemptCount.incrementAndGet();
                DistributedTransaction transaction = null;
                try {
                    transaction = manager.start();
                    T result = work.run(transaction);
                    transaction.commit();
                    return result;
                } catch (Exception e) {
                    handleTransactionException(e, transaction);
                    throw e;
                }
            });
        } catch (RetryException re) {
            Throwable cause = (re.getCause() != null) ? re.getCause() : re;
            throw new CustomException(cause.getMessage(), cause, determineErrorCode(cause));
        }
    }

    private void handleTransactionException(Exception e, DistributedTransaction transaction) {
        log.error(e.getMessage(), e);
        // UnknownTransactionStatusException: the commit outcome is unknown - do not roll back
        for (Throwable c = e; c != null; c = c.getCause()) {
            if (c instanceof UnknownTransactionStatusException) {
                log.warn("Transaction status is unknown - not rolling back. Verify whether it committed. txId={}", transaction != null ? transaction.getId() : "unknown");
                return;
            }
        }
        if (transaction != null) {
            try {
                transaction.rollback();
            } catch (Exception ex) {
                log.error(ex.getMessage(), ex);
            }
        }
    }

    // Same mapping as the table services
    private int determineErrorCode(Throwable e) {
        for (Throwable c = e; c != null; c = c.getCause()) {
            if (c instanceof CustomException && ((CustomException) c).getErrorCode() != null) {
                return ((CustomException) c).getErrorCode();
            }
        }
        for (Throwable c = e; c != null; c = c.getCause()) {
            if (c instanceof UnsatisfiedConditionException) return 9100;
            if (c instanceof UnknownTransactionStatusException) return 9200;
            if (c instanceof TransactionException
                    && (((TransactionException) c).isAuthenticationError()
                        || ((TransactionException) c).isAuthorizationError())) return 9600;
        }
        for (Throwable c = e; c != null; c = c.getCause()) {
            if (c instanceof TransactionException) return 9300;
        }
        if (e instanceof RuntimeException) return 9400;
        return 9500;
    }
}
