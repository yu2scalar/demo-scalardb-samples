package com.example.demo_scalardb_samples.exception;

import com.scalar.db.exception.transaction.TransactionException;
import com.scalar.db.sql.exception.SqlException;

public class CustomException extends RuntimeException {

    // Error code meaning "transaction status unknown" (UnknownTransactionStatusException).
    private static final int UNKNOWN_TRANSACTION_STATUS = 9200;

    private Integer errorCode;

    // ID of the transaction whose status is unknown (errorCode 9200); null otherwise.
    private String transactionId;

    public Integer getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(Integer errorCode) {
        this.errorCode = errorCode;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public CustomException(TransactionException ex, Integer errorCode) {
        super(ex.getMessage(), ex);
        this.setErrorCode(errorCode);
        this.transactionId = unknownStatusTransactionId(ex, errorCode);
    }

    public CustomException(Exception ex, Integer errorCode) {
        super(ex.getMessage(), ex);
        this.setErrorCode(errorCode);
        this.transactionId = unknownStatusTransactionId(ex, errorCode);
    }

    public CustomException(String message, Integer errorCode) {
        super(message);
        this.setErrorCode(errorCode);
    }

    public CustomException(String message, Throwable cause, Integer errorCode) {
        super(message, cause);
        this.setErrorCode(errorCode);
        this.transactionId = unknownStatusTransactionId(cause, errorCode);
    }

    /**
     * When the transaction status is unknown, find the transaction ID in the cause
     * chain so it can be returned to the client for verification.
     */
    private static String unknownStatusTransactionId(Throwable e, Integer errorCode) {
        if (errorCode == null || errorCode != UNKNOWN_TRANSACTION_STATUS) {
            return null;
        }
        for (Throwable c = e; c != null; c = c.getCause()) {
            if (c instanceof CustomException && ((CustomException) c).getTransactionId() != null) {
                return ((CustomException) c).getTransactionId();
            }
            if (c instanceof TransactionException) {
                return ((TransactionException) c).getTransactionId().orElse(null);
            }
            if (c instanceof SqlException) {
                return ((SqlException) c).getTransactionId().orElse(null);
            }
        }
        return null;
    }
}
