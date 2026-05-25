// src/main/java/com/katorymnd/pawapay/sdk/models/TransactionStatus.java
package com.katorymnd.pawapay.sdk.models;

/**
 * Response model for transaction status checks.
 * Reused for deposit, payout, and refund status queries.
 */
public class TransactionStatus {
    
    private String transactionId;  // Generic field for any transaction type
    private String depositId;      // Alias for depositId/refundId/payoutId
    private String status;
    private String type;           // "deposit", "payout", or "refund"
    private String amount;
    private String currency;
    private String createdAt;
    private String updatedAt;
    
    // Default constructor
    public TransactionStatus() {}
    
    // Getters
    public String getTransactionId() { return transactionId; }
    public String getDepositId() { return depositId; }
    public String getStatus() { return status; }
    public String getType() { return type; }
    public String getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getCreatedAt() { return createdAt; }
    public String getUpdatedAt() { return updatedAt; }
    
    // Setters
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
    public void setDepositId(String depositId) { this.depositId = depositId; }
    public void setStatus(String status) { this.status = status; }
    public void setType(String type) { this.type = type; }
    public void setAmount(String amount) { this.amount = amount; }
    public void setCurrency(String currency) { this.currency = currency; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}