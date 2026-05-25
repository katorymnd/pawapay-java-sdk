// src/main/java/com/katorymnd/pawapay/sdk/models/RefundResponse.java
package com.katorymnd.pawapay.sdk.models;

/**
 * Response model for refund initiation.
 */
public class RefundResponse {
    
    private String refundId;
    private String depositId;
    private String status;
    private String nextStep;
    
    // Default constructor
    public RefundResponse() {}
    
    // Getters
    public String getRefundId() { return refundId; }
    public String getDepositId() { return depositId; }
    public String getStatus() { return status; }
    public String getNextStep() { return nextStep; }
    
    // Setters
    public void setRefundId(String refundId) { this.refundId = refundId; }
    public void setDepositId(String depositId) { this.depositId = depositId; }
    public void setStatus(String status) { this.status = status; }
    public void setNextStep(String nextStep) { this.nextStep = nextStep; }
}