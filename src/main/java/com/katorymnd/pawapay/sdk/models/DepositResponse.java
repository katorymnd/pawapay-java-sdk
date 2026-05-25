package com.katorymnd.pawapay.sdk.models;

public class DepositResponse {
    private String depositId;
    private String status;
    private String nextStep;
    
    public String getDepositId() { return depositId; }
    public void setDepositId(String depositId) { this.depositId = depositId; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public String getNextStep() { return nextStep; }
    public void setNextStep(String nextStep) { this.nextStep = nextStep; }
}