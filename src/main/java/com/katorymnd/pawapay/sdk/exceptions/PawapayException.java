package com.katorymnd.pawapay.sdk.exceptions;

public class PawapayException extends Exception {
    private int statusCode;
    
    public PawapayException(String message) {
        super(message);
    }
    
    public PawapayException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }
    
    public int getStatusCode() {
        return statusCode;
    }
}