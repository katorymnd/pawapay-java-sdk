// src/main/java/com/katorymnd/pawapay/sdk/models/PayoutResponse.java
package com.katorymnd.pawapay.sdk.models;

/**
 * Response model for payout initiation.
 */
public class PayoutResponse {
    
    private String payoutId;
    private String status;
    private String nextStep;
    
    // Default constructor
    public PayoutResponse() {}
    
    // Getters
    public String getPayoutId() { return payoutId; }
    public String getStatus() { return status; }
    public String getNextStep() { return nextStep; }
    
    // Setters
    public void setPayoutId(String payoutId) { this.payoutId = payoutId; }
    public void setStatus(String status) { this.status = status; }
    public void setNextStep(String nextStep) { this.nextStep = nextStep; }
}