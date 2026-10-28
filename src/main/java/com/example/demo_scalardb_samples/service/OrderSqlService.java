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
import com.example.demo_scalardb_samples.repository.CustomerCustomersSqlRepository;
import com.example.demo_scalardb_samples.repository.InventoryItemsSqlRepository;
import com.example.demo_scalardb_samples.repository.OrderingOrdersSqlRepository;
import com.example.demo_scalardb_samples.repository.OrderingStatementsSqlRepository;
import com.scalar.db.sql.SqlSession;
import com.scalar.db.sql.SqlSessionFactory;
import com.scalar.db.sql.exception.AuthenticationException;
import com.scalar.db.sql.exception.AuthorizationException;
import com.scalar.db.sql.exception.SqlException;
import com.scalar.db.sql.exception.UnknownTransactionStatusException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.retry.RetryException;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.stereotype.Service;

/**
 * Same business logic as OrderService, on the SQL API.
 *
 * Each method is one SQL session (begin .. commit) and calls the SQL repositories
 * directly. Retry and error handling follow the table SQL services.
 * GetOrders has a single form: ScalarDB SQL decides whether the index is used.
 */
@Slf4j
@Service
public class OrderSqlService {
    SqlSessionFactory sqlSessionFactory;

    @Autowired
    @Qualifier("sqlApiRetryTemplate")
    private RetryTemplate sqlApiRetryTemplate;

    @Autowired
    CustomerCustomersSqlRepository customersRepository;

    @Autowired
    InventoryItemsSqlRepository itemsRepository;

    @Autowired
    OrderingOrdersSqlRepository ordersRepository;

    @Autowired
    OrderingStatementsSqlRepository statementsRepository;

    public OrderSqlService(SqlSessionFactory sqlSessionFactory) {
        this.sqlSessionFactory = sqlSessionFactory;
    }

    // PlaceOrder: reduce stock, charge the customer, write the order and its statements
    public PlaceOrderResponseDto placeOrder(PlaceOrderRequestDto request) throws CustomException {
        OrderRules.validate(request);
        return runInSession(session -> {
            String orderId = UUID.randomUUID().toString();
            List<OrderLineDto> lines = request.getItems();
            OrderingStatements[] statements = new OrderingStatements[lines.size()];
            long totalAmount = 0;
            for (int i : OrderRules.indexesByItemId(lines)) {
                OrderLineDto line = lines.get(i);
                InventoryItems item = itemsRepository.get(session,
                    InventoryItems.builder().itemId(line.getItemId()).build());
                if (item.getStock() < line.getQty()) {
                    throw new CustomException("Out of stock: item_id=" + line.getItemId()
                        + ", stock=" + item.getStock() + ", qty=" + line.getQty(), OrderRules.OUT_OF_STOCK);
                }
                item.setStock(item.getStock() - line.getQty());
                itemsRepository.update(session, item);
                totalAmount += item.getPrice() * line.getQty();
                statements[i] = OrderingStatements.builder()
                    .orderId(orderId)
                    .lineNo(i + 1)
                    .itemId(line.getItemId())
                    .qty(line.getQty())
                    .unitPrice(item.getPrice())
                    .build();
            }

            CustomerCustomers customer = customersRepository.get(session,
                CustomerCustomers.builder().customerId(request.getCustomerId()).build());
            if (customer.getCreditTotal() + totalAmount > customer.getCreditLimit()) {
                throw new CustomException("Credit limit exceeded: customer_id=" + request.getCustomerId()
                    + ", credit_total=" + customer.getCreditTotal() + ", amount=" + totalAmount
                    + ", credit_limit=" + customer.getCreditLimit(), OrderRules.CREDIT_LIMIT_EXCEEDED);
            }
            customer.setCreditTotal(customer.getCreditTotal() + totalAmount);
            customersRepository.update(session, customer);

            ordersRepository.insert(session, OrderingOrders.builder()
                .orderId(orderId)
                .customerId(request.getCustomerId())
                .createdAt(System.currentTimeMillis())
                .status(OrderRules.STATUS_ORDERED)
                .totalAmount(totalAmount)
                .build());
            for (OrderingStatements statement : statements) {
                statementsRepository.insert(session, statement);
            }
            return PlaceOrderResponseDto.builder().orderId(orderId).totalAmount(totalAmount).build();
        });
    }

    // GetOrder: the order, its statements and the item names
    public OrderDetailDto getOrder(String orderId) throws CustomException {
        return runInSession(session -> {
            OrderingOrders order = ordersRepository.get(session, OrderingOrders.builder().orderId(orderId).build());
            return buildOrderDetail(session, order);
        });
    }

    // GetOrders: all orders of a customer, newest first
    public List<OrderDetailDto> getOrdersByCustomerId(int customerId) throws CustomException {
        return runInSession(session -> {
            List<OrderingOrders> orders = ordersRepository.scanByCustomerId(session, customerId);
            orders.sort(Comparator.comparing(OrderingOrders::getCreatedAt).reversed());
            List<OrderDetailDto> details = new ArrayList<>();
            for (OrderingOrders order : orders) {
                details.add(buildOrderDetail(session, order));
            }
            return details;
        });
    }

    // CancelOrder: give back the stock and the credit, then mark the order CANCELLED.
    // The statements are kept.
    public ResponseStatusDto cancelOrder(String orderId) throws CustomException {
        return runInSession(session -> {
            OrderingOrders order = ordersRepository.get(session, OrderingOrders.builder().orderId(orderId).build());
            OrderRules.checkCancellable(orderId, order.getStatus());

            List<OrderingStatements> statements = statementsRepository.scan(session,
                OrderingStatements.builder().orderId(orderId).build());
            statements.sort(Comparator.comparing(OrderingStatements::getItemId));
            for (OrderingStatements statement : statements) {
                InventoryItems item = itemsRepository.get(session,
                    InventoryItems.builder().itemId(statement.getItemId()).build());
                item.setStock(item.getStock() + statement.getQty());
                itemsRepository.update(session, item);
            }

            CustomerCustomers customer = customersRepository.get(session,
                CustomerCustomers.builder().customerId(order.getCustomerId()).build());
            customer.setCreditTotal(customer.getCreditTotal() - order.getTotalAmount());
            customersRepository.update(session, customer);

            order.setStatus(OrderRules.STATUS_CANCELLED);
            ordersRepository.update(session, order);
            return ResponseStatusDto.builder().code(0).message("").build();
        });
    }

    private OrderDetailDto buildOrderDetail(SqlSession session, OrderingOrders order) throws Exception {
        List<OrderingStatements> statements = statementsRepository.scan(session,
            OrderingStatements.builder().orderId(order.getOrderId()).build());
        statements.sort(Comparator.comparing(OrderingStatements::getLineNo));
        List<OrderDetailLineDto> lines = new ArrayList<>();
        for (OrderingStatements statement : statements) {
            InventoryItems item = itemsRepository.get(session,
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

    @FunctionalInterface
    private interface SessionWork<T> {
        T run(SqlSession session) throws Exception;
    }

    // One SQL session (begin .. commit) with retry, as in the table SQL services
    private <T> T runInSession(SessionWork<T> work) throws CustomException {
        try {
            return sqlApiRetryTemplate.execute(() -> {
                SqlSession sqlSession = null;
                try {
                    sqlSession = sqlSessionFactory.createSqlSession();
                    sqlSession.begin();
                    T result = work.run(sqlSession);
                    sqlSession.commit();
                    return result;
                } catch (Exception e) {
                    handleSqlSessionException(e, sqlSession);
                    throw e;
                } finally {
                    if (sqlSession != null) {
                        sqlSession.close();
                    }
                }
            });
        } catch (RetryException re) {
            Throwable cause = (re.getCause() != null) ? re.getCause() : re;
            throw new CustomException(cause.getMessage(), cause, determineErrorCode(cause));
        }
    }

    private void handleSqlSessionException(Exception e, SqlSession sqlSession) {
        String txId = "unknown";
        for (Throwable c = e; c != null; c = c.getCause()) {
            if (c instanceof SqlException) {
                txId = ((SqlException) c).getTransactionId().orElse("unknown");
                break;
            }
        }
        log.error("Transaction failed. txId={}", txId, e);
        // UnknownTransactionStatusException: the commit outcome is unknown - do not roll back
        for (Throwable c = e; c != null; c = c.getCause()) {
            if (c instanceof UnknownTransactionStatusException) {
                log.warn("Transaction status is unknown - not rolling back. Verify whether it committed. txId={}", txId);
                return;
            }
        }
        if (sqlSession != null) {
            try {
                sqlSession.rollback();
            } catch (Exception ex) {
                log.error(ex.getMessage(), ex);
            }
        }
    }

    // Same mapping as the table SQL services
    private int determineErrorCode(Throwable e) {
        for (Throwable c = e; c != null; c = c.getCause()) {
            if (c instanceof CustomException && ((CustomException) c).getErrorCode() != null) {
                return ((CustomException) c).getErrorCode();
            }
        }
        for (Throwable c = e; c != null; c = c.getCause()) {
            if (c instanceof UnknownTransactionStatusException) return 9200;
            if (c instanceof AuthenticationException
                    || c instanceof AuthorizationException) return 9600;
        }
        for (Throwable c = e; c != null; c = c.getCause()) {
            if (c instanceof SqlException) return 9300;
        }
        if (e instanceof RuntimeException) return 9400;
        return 9500;
    }
}
