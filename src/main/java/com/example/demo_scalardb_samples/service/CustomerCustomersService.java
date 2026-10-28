package com.example.demo_scalardb_samples.service;

import com.example.demo_scalardb_samples.model.CustomerCustomers;
import com.example.demo_scalardb_samples.dto.CustomerCustomersDto;
import com.example.demo_scalardb_samples.dto.ResponseStatusDto;
import com.example.demo_scalardb_samples.exception.CustomException;
import com.example.demo_scalardb_samples.mapper.CustomerCustomersMapper;
import com.example.demo_scalardb_samples.repository.CustomerCustomersRepository;
import com.scalar.db.api.DistributedTransaction;
import com.scalar.db.api.DistributedTransactionManager;
import com.scalar.db.io.Key;
import com.scalar.db.exception.transaction.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.retry.RetryException;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;

/**
 * Service class with automatic retry logic for transient failures.
 *
 * This service uses Spring Framework's built-in retry support
 * (org.springframework.core.retry) to automatically retry operations that
 * fail due to transient exceptions like CommitConflictException. The checked
 * RetryException thrown by RetryTemplate.execute() is unwrapped to its cause
 * (the last original exception) before being converted to CustomException.
 *
 * Note: UnknownTransactionStatusException requires manual verification
 * and is NOT automatically retried to avoid duplicate operations.
 */
@Slf4j
@Service
public class CustomerCustomersService {
    DistributedTransactionManager manager;

    @Autowired
    @Qualifier("coreApiRetryTemplate")
    private RetryTemplate coreApiRetryTemplate;

    @Autowired
    CustomerCustomersRepository customersRepository;

    public CustomerCustomersService(DistributedTransactionManager manager) throws InstantiationException, IllegalAccessException {
        this.manager = manager;
    }

    // Create Record
    public ResponseStatusDto insertCustomerCustomers(CustomerCustomersDto customersDto) throws CustomException {
        try {
            return coreApiRetryTemplate.execute(() -> {
                DistributedTransaction transaction = null;
                try {
                    CustomerCustomers customers = CustomerCustomersMapper.mapToCustomerCustomers(customersDto);
                    transaction = manager.start();
                    customersRepository.insertCustomerCustomers(transaction, customers);
                    transaction.commit();
                    return ResponseStatusDto.builder().code(0).message("").build();
                } catch (Exception e) {
                    handleTransactionException(e, transaction);
                    throw e;
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

    // Upsert Record
    public ResponseStatusDto upsertCustomerCustomers(CustomerCustomersDto customersDto) throws CustomException {
        try {
            return coreApiRetryTemplate.execute(() -> {
                DistributedTransaction transaction = null;
                try {
                    CustomerCustomers customers = CustomerCustomersMapper.mapToCustomerCustomers(customersDto);
                    transaction = manager.start();
                    customersRepository.upsertCustomerCustomers(transaction, customers);
                    transaction.commit();
                    return ResponseStatusDto.builder().code(0).message("").build();
                } catch (Exception e) {
                    handleTransactionException(e, transaction);
                    throw e;
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

    // Retrieve Record
    public CustomerCustomersDto getCustomerCustomers(CustomerCustomersDto customersDto) throws CustomException {
        try {
            return coreApiRetryTemplate.execute(() -> {
                DistributedTransaction transaction = null;
                try {
                    CustomerCustomers customers = CustomerCustomersMapper.mapToCustomerCustomers(customersDto);
                    transaction = manager.start();
                    customers = customersRepository.getCustomerCustomers(transaction, customers);
                    transaction.commit();
                    return CustomerCustomersMapper.mapToCustomerCustomersDto(customers);
                } catch (Exception e) {
                    handleTransactionException(e, transaction);
                    throw e;
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

    // Update Record
    public ResponseStatusDto updateCustomerCustomers(CustomerCustomersDto customersDto) throws CustomException {
        try {
            return coreApiRetryTemplate.execute(() -> {
                DistributedTransaction transaction = null;
                try {
                    CustomerCustomers customers = CustomerCustomersMapper.mapToCustomerCustomers(customersDto);
                    transaction = manager.start();
                    customersRepository.updateCustomerCustomers(transaction, customers);
                    transaction.commit();
                    return ResponseStatusDto.builder().code(0).message("").build();
                } catch (Exception e) {
                    handleTransactionException(e, transaction);
                    throw e;
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

    // Delete Record
    public ResponseStatusDto deleteCustomerCustomers(CustomerCustomersDto customersDto) throws CustomException {
        try {
            return coreApiRetryTemplate.execute(() -> {
                DistributedTransaction transaction = null;
                try {
                    CustomerCustomers customers = CustomerCustomersMapper.mapToCustomerCustomers(customersDto);
                    transaction = manager.start();
                    customersRepository.deleteCustomerCustomers(transaction, customers);
                    transaction.commit();
                    return ResponseStatusDto.builder().code(0).message("").build();
                } catch (Exception e) {
                    handleTransactionException(e, transaction);
                    throw e;
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

    // Retrieve All Records
    public List<CustomerCustomersDto> getCustomerCustomersListAll() throws CustomException {
        try {
            return coreApiRetryTemplate.execute(() -> {
                DistributedTransaction transaction = null;
                List<CustomerCustomers> customersList = new ArrayList<>();
                try {
                    transaction = manager.start();
                    customersList = customersRepository.getCustomerCustomersListAll(transaction);
                    transaction.commit();
                    return CustomerCustomersMapper.mapToCustomerCustomersDtoList(customersList);
                } catch (Exception e) {
                    handleTransactionException(e, transaction);
                    throw e;
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

    // Retrieve Records by Partition Key
    public List<CustomerCustomersDto> getCustomerCustomersListByPk(CustomerCustomersDto customersDto) throws CustomException {
        try {
            return coreApiRetryTemplate.execute(() -> {
                DistributedTransaction transaction = null;
                List<CustomerCustomers> customersList = new ArrayList<>();
                try {
                    CustomerCustomers customers = CustomerCustomersMapper.mapToCustomerCustomers(customersDto);
                    Key partitionKey = customers.getPartitionKey();
                    transaction = manager.start();
                    customersList = customersRepository.getCustomerCustomersListByPk(transaction, partitionKey);
                    transaction.commit();
                    return CustomerCustomersMapper.mapToCustomerCustomersDtoList(customersList);
                } catch (Exception e) {
                    handleTransactionException(e, transaction);
                    throw e;
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

    private void handleTransactionException(Exception e, DistributedTransaction transaction) {
        log.error(e.getMessage(), e);
        // UnknownTransactionStatusException: the commit outcome is unknown. As in the
        // official API guide, do nothing here (no rollback) - the application must check
        // whether the transaction committed, and retry only if it did not.
        // https://scalardb.scalar-labs.com/docs/latest/api-guide/#commitexception-commitconflictexception-and-unknowntransactionstatusexception
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
        // Specific subtypes before their parent type; scan the whole cause
        // chain so wrapped exceptions are still classified.
        for (Throwable c = e; c != null; c = c.getCause()) {
            if (c instanceof UnsatisfiedConditionException) return 9100;
            if (c instanceof UnknownTransactionStatusException) return 9200;
            // With ScalarDB Cluster auth enabled, authentication / authorization
            // failures arrive as a TransactionException with a flag set.
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
