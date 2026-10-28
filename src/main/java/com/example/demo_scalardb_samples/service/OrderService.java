package com.example.demo_scalardb_samples.service;

import com.example.demo_scalardb_samples.dto.OrderDetailDto;
import com.example.demo_scalardb_samples.dto.OrderDetailLineDto;
import com.example.demo_scalardb_samples.dto.OrderLineDto;
import com.example.demo_scalardb_samples.dto.PlaceOrderRequestDto;
import com.example.demo_scalardb_samples.dto.PlaceOrderResponseDto;
import com.example.demo_scalardb_samples.dto.ResponseStatusDto;
import com.example.demo_scalardb_samples.exception.CustomException;
import com.example.demo_scalardb_samples.model.CustomerCustomers;
import com.example.demo_scalardb_samples.model.InventoryItems;
import com.example.demo_scalardb_samples.model.OrderingOrders;
import com.example.demo_scalardb_samples.model.OrderingStatements;
import com.example.demo_scalardb_samples.repository.CustomerCustomersRepository;
import com.example.demo_scalardb_samples.repository.InventoryItemsRepository;
import com.example.demo_scalardb_samples.repository.OrderingOrdersRepository;
import com.example.demo_scalardb_samples.repository.OrderingStatementsRepository;
import com.scalar.db.api.DistributedTransaction;
import com.scalar.db.api.DistributedTransactionManager;
import com.scalar.db.exception.transaction.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.retry.RetryException;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.stereotype.Service;

/**
 * Business logic of scalardb-sample (PlaceOrder / GetOrder / GetOrders) plus
 * CancelOrder, on the Java CRUD API.
 *
 * Each method is one transaction and calls the repositories directly (not the
 * table services, which start their own transaction per call). Retry and error
 * handling follow the table services.
 */
@Slf4j
@Service
public class OrderService {
    DistributedTransactionManager manager;

    // Transaction attempts including retries; the load test reads the difference
    private final AtomicLong attemptCount = new AtomicLong();

    @Autowired
    @Qualifier("coreApiRetryTemplate")
    private RetryTemplate coreApiRetryTemplate;

    @Autowired
    CustomerCustomersRepository customersRepository;

    @Autowired
    InventoryItemsRepository itemsRepository;

    @Autowired
    OrderingOrdersRepository ordersRepository;

    @Autowired
    OrderingStatementsRepository statementsRepository;

    public OrderService(DistributedTransactionManager manager) {
        this.manager = manager;
    }

    // PlaceOrder: reduce stock, charge the customer, write the order and its statements
    public PlaceOrderResponseDto placeOrder(PlaceOrderRequestDto request) throws CustomException {
        OrderRules.validate(request);
        return runInTransaction(transaction -> {
            String orderId = UUID.randomUUID().toString();
            List<OrderLineDto> lines = request.getItems();
            OrderingStatements[] statements = new OrderingStatements[lines.size()];
            long totalAmount = 0;
            for (int i : OrderRules.indexesByItemId(lines)) {
                OrderLineDto line = lines.get(i);
                InventoryItems item = itemsRepository.getInventoryItems(transaction,
                    InventoryItems.builder().itemId(line.getItemId()).build());
                if (item.getStock() < line.getQty()) {
                    throw new CustomException("Out of stock: item_id=" + line.getItemId()
                        + ", stock=" + item.getStock() + ", qty=" + line.getQty(), OrderRules.OUT_OF_STOCK);
                }
                item.setStock(item.getStock() - line.getQty());
                itemsRepository.updateInventoryItems(transaction, item);
                totalAmount += item.getPrice() * line.getQty();
                statements[i] = OrderingStatements.builder()
                    .orderId(orderId)
                    .lineNo(i + 1)
                    .itemId(line.getItemId())
                    .qty(line.getQty())
                    .unitPrice(item.getPrice())
                    .build();
            }

            CustomerCustomers customer = customersRepository.getCustomerCustomers(transaction,
                CustomerCustomers.builder().customerId(request.getCustomerId()).build());
            if (customer.getCreditTotal() + totalAmount > customer.getCreditLimit()) {
                throw new CustomException("Credit limit exceeded: customer_id=" + request.getCustomerId()
                    + ", credit_total=" + customer.getCreditTotal() + ", amount=" + totalAmount
                    + ", credit_limit=" + customer.getCreditLimit(), OrderRules.CREDIT_LIMIT_EXCEEDED);
            }
            customer.setCreditTotal(customer.getCreditTotal() + totalAmount);
            customersRepository.updateCustomerCustomers(transaction, customer);

            ordersRepository.insertOrderingOrders(transaction, OrderingOrders.builder()
                .orderId(orderId)
                .customerId(request.getCustomerId())
                .createdAt(System.currentTimeMillis())
                .status(OrderRules.STATUS_ORDERED)
                .totalAmount(totalAmount)
                .build());
            for (OrderingStatements statement : statements) {
                statementsRepository.insertOrderingStatements(transaction, statement);
            }
            return PlaceOrderResponseDto.builder().orderId(orderId).totalAmount(totalAmount).build();
        });
    }

    // GetOrder: the order, its statements and the item names
    public OrderDetailDto getOrder(String orderId) throws CustomException {
        return runInTransaction(transaction -> {
            OrderingOrders order = ordersRepository.getOrderingOrders(transaction,
                OrderingOrders.builder().orderId(orderId).build());
            return buildOrderDetail(transaction, order);
        });
    }

    // GetOrders: all orders of a customer, newest first.
    // method = "index" (secondary index) or "scan" (ScanAll + where)
    public List<OrderDetailDto> getOrdersByCustomerId(int customerId, String method) throws CustomException {
        boolean useIndex;
        if ("index".equals(method)) {
            useIndex = true;
        } else if ("scan".equals(method)) {
            useIndex = false;
        } else {
            throw new CustomException("method must be index or scan: " + method, OrderRules.INVALID_REQUEST);
        }
        return runInTransaction(transaction -> {
            List<OrderingOrders> orders = useIndex
                ? ordersRepository.getOrderingOrdersListByCustomerIdIndex(transaction, customerId)
                : ordersRepository.getOrderingOrdersListByCustomerIdScan(transaction, customerId);
            orders.sort(Comparator.comparing(OrderingOrders::getCreatedAt).reversed());
            List<OrderDetailDto> details = new ArrayList<>();
            for (OrderingOrders order : orders) {
                details.add(buildOrderDetail(transaction, order));
            }
            return details;
        });
    }

    // CancelOrder: give back the stock and the credit, then mark the order CANCELLED.
    // The statements are kept.
    public ResponseStatusDto cancelOrder(String orderId) throws CustomException {
        return runInTransaction(transaction -> {
            OrderingOrders order = ordersRepository.getOrderingOrders(transaction,
                OrderingOrders.builder().orderId(orderId).build());
            OrderRules.checkCancellable(orderId, order.getStatus());

            List<OrderingStatements> statements = statementsRepository.getOrderingStatementsListByPk(transaction,
                OrderingStatements.builder().orderId(orderId).build().getPartitionKey());
            statements.sort(Comparator.comparing(OrderingStatements::getItemId));
            for (OrderingStatements statement : statements) {
                InventoryItems item = itemsRepository.getInventoryItems(transaction,
                    InventoryItems.builder().itemId(statement.getItemId()).build());
                item.setStock(item.getStock() + statement.getQty());
                itemsRepository.updateInventoryItems(transaction, item);
            }

            CustomerCustomers customer = customersRepository.getCustomerCustomers(transaction,
                CustomerCustomers.builder().customerId(order.getCustomerId()).build());
            customer.setCreditTotal(customer.getCreditTotal() - order.getTotalAmount());
            customersRepository.updateCustomerCustomers(transaction, customer);

            order.setStatus(OrderRules.STATUS_CANCELLED);
            ordersRepository.updateOrderingOrders(transaction, order);
            return ResponseStatusDto.builder().code(0).message("").build();
        });
    }

    private OrderDetailDto buildOrderDetail(DistributedTransaction transaction, OrderingOrders order) throws CrudException {
        List<OrderingStatements> statements = statementsRepository.getOrderingStatementsListByPk(transaction,
            OrderingStatements.builder().orderId(order.getOrderId()).build().getPartitionKey());
        List<OrderDetailLineDto> lines = new ArrayList<>();
        for (OrderingStatements statement : statements) {
            InventoryItems item = itemsRepository.getInventoryItems(transaction,
                InventoryItems.builder().itemId(statement.getItemId()).build());
            lines.add(OrderDetailLineDto.builder()
                .lineNo(statement.getLineNo())
                .itemId(statement.getItemId())
                .itemName(item.getName())
                .qty(statement.getQty())
                .unitPrice(statement.getUnitPrice())
                .subtotal(statement.getUnitPrice() * statement.getQty())
                .build());
        }
        return OrderDetailDto.builder()
            .orderId(order.getOrderId())
            .customerId(order.getCustomerId())
            .createdAt(order.getCreatedAt())
            .status(order.getStatus())
            .totalAmount(order.getTotalAmount())
            .items(lines)
            .build();
    }

    public long getAttemptCount() {
        return attemptCount.get();
    }

    @FunctionalInterface
    private interface TransactionWork<T> {
        T run(DistributedTransaction transaction) throws Exception;
    }

    // One transaction with retry, as in the table services
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
