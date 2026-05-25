package com.katorymnd.pawapay.sdk.models;

import com.katorymnd.pawapay.sdk.models.common.Money;
import com.katorymnd.pawapay.sdk.models.common.Party;

public class DepositRequest {
    private Money amount;
    private Party payer;
    private String customerMessage;
    
    public Money getAmount() { return amount; }
    public void setAmount(Money amount) { this.amount = amount; }
    
    public Party getPayer() { return payer; }
    public void setPayer(Party payer) { this.payer = payer; }
    
    public String getCustomerMessage() { return customerMessage; }
    public void setCustomerMessage(String customerMessage) { this.customerMessage = customerMessage; }
}