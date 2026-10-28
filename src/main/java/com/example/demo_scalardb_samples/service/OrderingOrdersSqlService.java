package com.example.demo_scalardb_samples.service;

import com.example.demo_scalardb_samples.model.OrderingOrders;
import com.example.demo_scalardb_samples.dto.OrderingOrdersDto;
import com.example.demo_scalardb_samples.dto.ResponseStatusDto;
import com.example.demo_scalardb_samples.dto.SqlCommandDto;
import com.example.demo_scalardb_samples.exception.CustomException;
import com.example.demo_scalardb_samples.mapper.OrderingOrdersMapper;
import com.example.demo_scalardb_samples.repository.OrderingOrdersSqlRepository;
import com.scalar.db.sql.SqlSession;
import com.scalar.db.sql.SqlSessionFactory;
import com.scalar.db.sql.exception.AuthenticationException;
import com.scalar.db.sql.exception.AuthorizationException;
import com.scalar.db.sql.exception.SqlException;
import com.scalar.db.sql.exception.UnknownTransactionStatusException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.retry.RetryException;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * SQL Service class with automatic retry logic for transient failures.
 *
 * This service uses Spring Framework's built-in retry support
 * (org.springframework.core.retry) to automatically retry SQL operations that
 * fail due to transient exceptions like TransactionRetryableException. The
 * checked RetryException thrown by RetryTemplate.execute() is unwrapped to its
 * cause (the last original exception) before being converted to CustomException.
 *
 * Note: UnknownTransactionStatusException requires manual verification
 * and is NOT automatically retried to avoid duplicate operations.
 */
@Slf4j
@Service
public class OrderingOrdersSqlService {
    SqlSessionFactory sqlSessionFactory;

    @Autowired
    @Qualifier("sqlApiRetryTemplate")
    private RetryTemplate sqlApiRetryTemplate;

    @Autowired
    OrderingOrdersSqlRepository ordersRepository;

    public OrderingOrdersSqlService(SqlSessionFactory sqlSessionFactory) {
        this.sqlSessionFactory = sqlSessionFactory;
    }

    // INSERT using SQL
    public ResponseStatusDto insertOrderingOrders(OrderingOrdersDto ordersDto) throws CustomException {
        try {
            return sqlApiRetryTemplate.execute(() -> {
                SqlSession sqlSession = null;
                try {
                    OrderingOrders orders = OrderingOrdersMapper.mapToOrderingOrders(ordersDto);
                    sqlSession = sqlSessionFactory.createSqlSession();
                    sqlSession.begin();

                    orders = ordersRepository.insert(sqlSession, orders);

                    sqlSession.commit();
                    return ResponseStatusDto.builder().code(0).message("").build();
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
            // execute() always wraps the failure; the cause is the last
            // original exception (earlier attempts are attached as suppressed).
            // Unwrap it and translate into the app's user-facing error contract
            // (CustomException) so internal exceptions never leak to API
            // clients as-is — customize this mapping to your needs.
            Throwable cause = (re.getCause() != null) ? re.getCause() : re;
            throw new CustomException(cause.getMessage(), cause, determineErrorCode(cause));
        }
    }

    // UPSERT using SQL (insert or update if exists)
    public ResponseStatusDto upsertOrderingOrders(OrderingOrdersDto ordersDto) throws CustomException {
        try {
            return sqlApiRetryTemplate.execute(() -> {
                SqlSession sqlSession = null;
                try {
                    OrderingOrders orders = OrderingOrdersMapper.mapToOrderingOrders(ordersDto);
                    sqlSession = sqlSessionFactory.createSqlSession();
                    sqlSession.begin();

                    orders = ordersRepository.upsert(sqlSession, orders);

                    sqlSession.commit();
                    return ResponseStatusDto.builder().code(0).message("").build();
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
            // execute() always wraps the failure; the cause is the last
            // original exception (earlier attempts are attached as suppressed).
            // Unwrap it and translate into the app's user-facing error contract
            // (CustomException) so internal exceptions never leak to API
            // clients as-is — customize this mapping to your needs.
            Throwable cause = (re.getCause() != null) ? re.getCause() : re;
            throw new CustomException(cause.getMessage(), cause, determineErrorCode(cause));
        }
    }

    // SELECT using SQL (get by partition and clustering key)
    public OrderingOrdersDto getOrderingOrders(OrderingOrdersDto ordersDto) throws CustomException {
        try {
            return sqlApiRetryTemplate.execute(() -> {
                SqlSession sqlSession = null;
                try {
                    OrderingOrders orders = OrderingOrdersMapper.mapToOrderingOrders(ordersDto);
                    sqlSession = sqlSessionFactory.createSqlSession();
                    sqlSession.begin();

                    orders = ordersRepository.get(sqlSession, orders);

                    sqlSession.commit();
                    return OrderingOrdersMapper.mapToOrderingOrdersDto(orders);
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
            // execute() always wraps the failure; the cause is the last
            // original exception (earlier attempts are attached as suppressed).
            // Unwrap it and translate into the app's user-facing error contract
            // (CustomException) so internal exceptions never leak to API
            // clients as-is — customize this mapping to your needs.
            Throwable cause = (re.getCause() != null) ? re.getCause() : re;
            throw new CustomException(cause.getMessage(), cause, determineErrorCode(cause));
        }
    }

    // UPDATE using SQL
    public ResponseStatusDto updateOrderingOrders(OrderingOrdersDto ordersDto) throws CustomException {
        try {
            return sqlApiRetryTemplate.execute(() -> {
                SqlSession sqlSession = null;
                try {
                    OrderingOrders orders = OrderingOrdersMapper.mapToOrderingOrders(ordersDto);
                    sqlSession = sqlSessionFactory.createSqlSession();
                    sqlSession.begin();

                    orders = ordersRepository.update(sqlSession, orders);

                    sqlSession.commit();
                    return ResponseStatusDto.builder().code(0).message("").build();
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
            // execute() always wraps the failure; the cause is the last
            // original exception (earlier attempts are attached as suppressed).
            // Unwrap it and translate into the app's user-facing error contract
            // (CustomException) so internal exceptions never leak to API
            // clients as-is — customize this mapping to your needs.
            Throwable cause = (re.getCause() != null) ? re.getCause() : re;
            throw new CustomException(cause.getMessage(), cause, determineErrorCode(cause));
        }
    }

    // DELETE using SQL
    public ResponseStatusDto deleteOrderingOrders(OrderingOrdersDto ordersDto) throws CustomException {
        try {
            return sqlApiRetryTemplate.execute(() -> {
                SqlSession sqlSession = null;
                try {
                    OrderingOrders orders = OrderingOrdersMapper.mapToOrderingOrders(ordersDto);
                    sqlSession = sqlSessionFactory.createSqlSession();
                    sqlSession.begin();

                    ordersRepository.delete(sqlSession, orders);

                    sqlSession.commit();
                    return ResponseStatusDto.builder().code(0).message("").build();
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
            // execute() always wraps the failure; the cause is the last
            // original exception (earlier attempts are attached as suppressed).
            // Unwrap it and translate into the app's user-facing error contract
            // (CustomException) so internal exceptions never leak to API
            // clients as-is — customize this mapping to your needs.
            Throwable cause = (re.getCause() != null) ? re.getCause() : re;
            throw new CustomException(cause.getMessage(), cause, determineErrorCode(cause));
        }
    }

    // SELECT * (scan all) using SQL
    public List<OrderingOrdersDto> getOrderingOrdersListAll() throws CustomException {
        try {
            return sqlApiRetryTemplate.execute(() -> {
                SqlSession sqlSession = null;
                List<OrderingOrders> ordersList = new ArrayList<>();
                try {
                    sqlSession = sqlSessionFactory.createSqlSession();
                    sqlSession.begin();

                    ordersList = ordersRepository.scan(sqlSession);

                    sqlSession.commit();
                    return OrderingOrdersMapper.mapToOrderingOrdersDtoList(ordersList);
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
            // execute() always wraps the failure; the cause is the last
            // original exception (earlier attempts are attached as suppressed).
            // Unwrap it and translate into the app's user-facing error contract
            // (CustomException) so internal exceptions never leak to API
            // clients as-is — customize this mapping to your needs.
            Throwable cause = (re.getCause() != null) ? re.getCause() : re;
            throw new CustomException(cause.getMessage(), cause, determineErrorCode(cause));
        }
    }

    // SELECT with partition key using SQL
    public List<OrderingOrdersDto> getOrderingOrdersListByPk(OrderingOrdersDto ordersDto) throws CustomException {
        try {
            return sqlApiRetryTemplate.execute(() -> {
                SqlSession sqlSession = null;
                List<OrderingOrders> ordersList = new ArrayList<>();
                try {
                    OrderingOrders orders = OrderingOrdersMapper.mapToOrderingOrders(ordersDto);
                    sqlSession = sqlSessionFactory.createSqlSession();
                    sqlSession.begin();

                    ordersList = ordersRepository.scan(sqlSession, orders);

                    sqlSession.commit();
                    return OrderingOrdersMapper.mapToOrderingOrdersDtoList(ordersList);
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
            // execute() always wraps the failure; the cause is the last
            // original exception (earlier attempts are attached as suppressed).
            // Unwrap it and translate into the app's user-facing error contract
            // (CustomException) so internal exceptions never leak to API
            // clients as-is — customize this mapping to your needs.
            Throwable cause = (re.getCause() != null) ? re.getCause() : re;
            throw new CustomException(cause.getMessage(), cause, determineErrorCode(cause));
        }
    }

    // Generic SQL execution (for flexibility - allows custom queries)
    public List<OrderingOrdersDto> executeSQL(SqlCommandDto sqlCommandDto) throws CustomException {
        try {
            return sqlApiRetryTemplate.execute(() -> {
                SqlSession sqlSession = null;
                List<OrderingOrders> ordersList = new ArrayList<>();
                try {
                    sqlSession = sqlSessionFactory.createSqlSession();
                    sqlSession.begin();

                    ordersList = ordersRepository.executeQuery(sqlSession, sqlCommandDto.getSqlCommand());

                    sqlSession.commit();
                    return OrderingOrdersMapper.mapToOrderingOrdersDtoList(ordersList);
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
            // execute() always wraps the failure; the cause is the last
            // original exception (earlier attempts are attached as suppressed).
            // Unwrap it and translate into the app's user-facing error contract
            // (CustomException) so internal exceptions never leak to API
            // clients as-is — customize this mapping to your needs.
            Throwable cause = (re.getCause() != null) ? re.getCause() : re;
            throw new CustomException(cause.getMessage(), cause, determineErrorCode(cause));
        }
    }

    private void handleSqlSessionException(Exception e, SqlSession sqlSession) {
        // The SqlException may be wrapped (e.g. in RepositoryException) - scan the cause chain.
        String txId = "unknown";
        for (Throwable c = e; c != null; c = c.getCause()) {
            if (c instanceof SqlException) {
                txId = ((SqlException) c).getTransactionId().orElse("unknown");
                break;
            }
        }
        log.error("Transaction failed. txId={}", txId, e);
        // UnknownTransactionStatusException: the commit outcome is unknown. As in the
        // official API guide, do nothing here (no rollback) - the application must check
        // whether the transaction committed, and retry only if it did not.
        // https://scalardb.scalar-labs.com/docs/latest/api-guide/#commitexception-commitconflictexception-and-unknowntransactionstatusexception
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

    /**
     * Determine error code based on exception type.
     *
     * Note: UnknownTransactionStatusException requires manual verification
     * before retry to avoid duplicate operations. This exception is NOT
     * automatically retried.
     */
    private int determineErrorCode(Throwable e) {
        // A CustomException already carries a code determined closer to the
        // source (participant response / repository) - preserve it.
        for (Throwable c = e; c != null; c = c.getCause()) {
            if (c instanceof CustomException && ((CustomException) c).getErrorCode() != null) {
                return ((CustomException) c).getErrorCode();
            }
        }
        // UnknownTransactionStatusException / AuthenticationException /
        // AuthorizationException are subclasses of SqlException - check them
        // before the parent type; scan the whole cause chain so wrapped
        // exceptions (e.g. RepositoryException) are still classified.
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
