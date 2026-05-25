package com.katorymnd.pawapay.sdk.models.common;

public class Party {
    private String partyIdType;
    private String partyId;
    private String provider;
    
    public Party() {}
    public Party(String partyIdType, String partyId, String provider) {
        this.partyIdType = partyIdType;
        this.partyId = partyId;
        this.provider = provider;
    }
    
    // Getters and setters
    public String getPartyIdType() { return partyIdType; }
    public void setPartyIdType(String partyIdType) { this.partyIdType = partyIdType; }
    
    public String getPartyId() { return partyId; }
    public void setPartyId(String partyId) { this.partyId = partyId; }
    
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
}