// src/main/java/com/katorymnd/pawapay/sdk/models/PayoutRequest.java
package com.katorymnd.pawapay.sdk.models;

import com.katorymnd.pawapay.sdk.models.common.Money;
import com.katorymnd.pawapay.sdk.models.common.Party;

import java.util.List;

/**
 * Request model for initiating a payout transaction.
 */
public class PayoutRequest {
    
    private Money amount;
    private Party recipient;
    private String customerMessage;
    private List<Object> metadata;
    
    // Default constructor
    public PayoutRequest() {}
    
    // Getters
    public Money getAmount() { return amount; }
    public Party getRecipient() { return recipient; }
    public String getCustomerMessage() { return customerMessage; }
    public List<Object> getMetadata() { return metadata; }
    
    // Setters
    public void setAmount(Money amount) { this.amount = amount; }
    public void setRecipient(Party recipient) { this.recipient = recipient; }
    public void setCustomerMessage(String customerMessage) { this.customerMessage = customerMessage; }
    public void setMetadata(List<Object> metadata) { this.metadata = metadata; }
}