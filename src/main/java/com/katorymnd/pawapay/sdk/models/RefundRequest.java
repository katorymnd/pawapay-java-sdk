// src/main/java/com/katorymnd/pawapay/sdk/models/RefundRequest.java
package com.katorymnd.pawapay.sdk.models;

import java.util.List;

/**
 * Request model for initiating a refund transaction.
 */
public class RefundRequest {
    
    private String refundId;
    private String depositId;
    private String amount;
    private String currency;
    private List<Object> metadata;
    
    // Default constructor
    public RefundRequest() {}
    
    // Getters
    public String getRefundId() { return refundId; }
    public String getDepositId() { return depositId; }
    public String getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public List<Object> getMetadata() { return metadata; }
    
    // Setters
    public void setRefundId(String refundId) { this.refundId = refundId; }
    public void setDepositId(String depositId) { this.depositId = depositId; }
    public void setAmount(String amount) { this.amount = amount; }
    public void setCurrency(String currency) { this.currency = currency; }
    public void setMetadata(List<Object> metadata) { this.metadata = metadata; }
}