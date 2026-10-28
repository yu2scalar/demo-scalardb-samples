package com.example.demo_scalardb_samples.repository;

import com.example.demo_scalardb_samples.exception.CustomException;
import com.example.demo_scalardb_samples.model.CustomerCustomers;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.scalar.db.api.*;
import com.scalar.db.exception.transaction.*;
import com.scalar.db.io.Key;
import org.springframework.stereotype.Repository;

@Repository
public class CustomerCustomersRepository {

    private int scanLimit = 100; // Default scan limit
    
    public void setScanLimit(int scanLimit) {
        this.scanLimit = scanLimit;
    }
    
    public int getScanLimit() {
        return scanLimit;
    }

    // Get Record by Partition & Clustering Key
    public CustomerCustomers getCustomerCustomers(DistributedTransaction transaction, CustomerCustomers customers) throws CrudException {
        Key partitionKey = customers.getPartitionKey();
        
        Get get = Get.newBuilder()
            .namespace(CustomerCustomers.NAMESPACE)
            .table(CustomerCustomers.TABLE)
            .partitionKey(partitionKey)
            
            .projections(CustomerCustomers.CUSTOMER_ID, CustomerCustomers.NAME, CustomerCustomers.CREDIT_LIMIT, CustomerCustomers.CREDIT_TOTAL)
            .build();
        Optional<Result> result = transaction.get(get);
        if (result.isEmpty()) {
            throw new CustomException("No record found in CustomerCustomers", 9700);
        }
        return buildCustomerCustomers(result.get());
    }

    // Insert Record
    public CustomerCustomers insertCustomerCustomers(DistributedTransaction transaction, CustomerCustomers customers) throws CrudException {
        Key partitionKey = customers.getPartitionKey();
        
        Insert insert = Insert.newBuilder()
            .namespace(CustomerCustomers.NAMESPACE)
            .table(CustomerCustomers.TABLE)
            .partitionKey(partitionKey)
            .textValue(CustomerCustomers.NAME, customers.getName())
            .bigIntValue(CustomerCustomers.CREDIT_LIMIT, customers.getCreditLimit())
            .bigIntValue(CustomerCustomers.CREDIT_TOTAL, customers.getCreditTotal())
            .build();
        transaction.insert(insert);
        return customers;
    }

    // Update Record
    public CustomerCustomers updateCustomerCustomers(DistributedTransaction transaction, CustomerCustomers customers) throws CrudException {
        Key partitionKey = customers.getPartitionKey();
        
        MutationCondition condition = ConditionBuilder.updateIfExists();

        Update update = Update.newBuilder()
            .namespace(CustomerCustomers.NAMESPACE)
            .table(CustomerCustomers.TABLE)
            .partitionKey(partitionKey)
            .textValue(CustomerCustomers.NAME, customers.getName())
            .bigIntValue(CustomerCustomers.CREDIT_LIMIT, customers.getCreditLimit())
            .bigIntValue(CustomerCustomers.CREDIT_TOTAL, customers.getCreditTotal())
            .condition(condition)
            .build();
        transaction.update(update);
        return customers;
    }

    // Upsert Record
    public CustomerCustomers upsertCustomerCustomers(DistributedTransaction transaction, CustomerCustomers customers) throws CrudException {
        Key partitionKey = customers.getPartitionKey();
        
        Upsert upsert = Upsert.newBuilder()
            .namespace(CustomerCustomers.NAMESPACE)
            .table(CustomerCustomers.TABLE)
            .partitionKey(partitionKey)
            .textValue(CustomerCustomers.NAME, customers.getName())
            .bigIntValue(CustomerCustomers.CREDIT_LIMIT, customers.getCreditLimit())
            .bigIntValue(CustomerCustomers.CREDIT_TOTAL, customers.getCreditTotal())
            .build();
        transaction.upsert(upsert);
        return customers;
    }

    // Delete Record
    public void deleteCustomerCustomers(DistributedTransaction transaction, CustomerCustomers customers) throws CrudException {
        Key partitionKey = customers.getPartitionKey();
        
        MutationCondition condition = ConditionBuilder.deleteIfExists();
        Delete delete = Delete.newBuilder()
            .namespace(CustomerCustomers.NAMESPACE)
            .table(CustomerCustomers.TABLE)
            .partitionKey(partitionKey)
            
            .condition(condition)
            .build();
        transaction.delete(delete);
    }

    // Scan All Records
    public List<CustomerCustomers> getCustomerCustomersListAll(DistributedTransaction transaction) throws CrudException {
        Scan scan = Scan.newBuilder()
            .namespace(CustomerCustomers.NAMESPACE)
            .table(CustomerCustomers.TABLE)
            .all()
            .projections(CustomerCustomers.CUSTOMER_ID, CustomerCustomers.NAME, CustomerCustomers.CREDIT_LIMIT, CustomerCustomers.CREDIT_TOTAL)
            .limit(scanLimit)
            .build();
        List<Result> results = transaction.scan(scan);
        List<CustomerCustomers> customersList = new ArrayList<>();
        for (Result result : results) {
            customersList.add(buildCustomerCustomers(result));
        }
        return customersList;
    }

    // Scan Records by Partition Key
    public List<CustomerCustomers> getCustomerCustomersListByPk(DistributedTransaction transaction, Key partitionKey) throws CrudException {
        Scan scan = Scan.newBuilder()
            .namespace(CustomerCustomers.NAMESPACE)
            .table(CustomerCustomers.TABLE)
            .partitionKey(partitionKey)
            .projections(CustomerCustomers.CUSTOMER_ID, CustomerCustomers.NAME, CustomerCustomers.CREDIT_LIMIT, CustomerCustomers.CREDIT_TOTAL)
            .limit(scanLimit)
            .build();
        List<Result> results = transaction.scan(scan);
        List<CustomerCustomers> customersList = new ArrayList<>();
        for (Result result : results) {
            customersList.add(buildCustomerCustomers(result));
        }
        return customersList;
    }

    // Object Builder from ScalarDB Result
    private CustomerCustomers buildCustomerCustomers(Result result) {
        return CustomerCustomers.builder()
            .customerId(result.getInt(CustomerCustomers.CUSTOMER_ID))
            .name(result.getText(CustomerCustomers.NAME))
            .creditLimit(result.getBigInt(CustomerCustomers.CREDIT_LIMIT))
            .creditTotal(result.getBigInt(CustomerCustomers.CREDIT_TOTAL))
            .build();
    }
}