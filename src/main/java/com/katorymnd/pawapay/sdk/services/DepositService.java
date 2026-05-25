// src/main/java/com/katorymnd/pawapay/sdk/services/DepositService.java
package com.katorymnd.pawapay.sdk.services;

import com.katorymnd.pawapay.sdk.api.ApiClient;
import com.katorymnd.pawapay.sdk.exceptions.PawapayException;
import com.katorymnd.pawapay.sdk.models.DepositRequest;
import com.katorymnd.pawapay.sdk.models.DepositResponse;
import com.katorymnd.pawapay.sdk.models.TransactionStatus;

import java.util.Map;

public class DepositService {

    private final ApiClient apiClient;

    public DepositService(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    @SuppressWarnings("unchecked")
    public DepositResponse initiateDeposit(DepositRequest request) throws PawapayException {
        // Defensive null checks to avoid NPE before API call
        if (request == null || request.getAmount() == null || request.getPayer() == null) {
            throw new PawapayException("Invalid deposit request: missing amount or payer");
        }

        try {
            Map<String, Object> raw = apiClient.initiateDepositV2(
                null, // depositId - let API generate
                request.getAmount().getAmount(),
                request.getAmount().getCurrency(),
                request.getPayer().getPartyId(),
                request.getPayer().getProvider(),
                request.getCustomerMessage(),
                null, null, null
            ).join();

            int status = (int) raw.get("status");
            if (status < 200 || status >= 300) {
                Object resp = raw.get("response");
                String errorMsg = resp != null ? resp.toString() : "Unknown API error";
                throw new PawapayException("API error: " + errorMsg, status);
            }

            Map<String, Object> data = (Map<String, Object>) raw.get("response");
            DepositResponse resp = new DepositResponse();
            if (data != null) {
                resp.setDepositId((String) data.get("depositId"));
                resp.setStatus((String) data.get("status"));
                resp.setNextStep((String) data.get("nextStep"));
            }
            return resp;
        } catch (PawapayException e) {
            throw e;
        } catch (Exception e) {
            throw new PawapayException("Deposit failed: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    public TransactionStatus getDepositStatus(String depositId) throws PawapayException {
        if (depositId == null || depositId.isEmpty()) {
            throw new PawapayException("Invalid depositId: cannot be null or empty");
        }

        try {
            Map<String, Object> raw = apiClient.checkTransactionStatusAuto(depositId, "deposit").join();
            
            int status = (int) raw.get("status");
            if (status < 200 || status >= 300) {
                Object resp = raw.get("response");
                String errorMsg = resp != null ? resp.toString() : "Unknown API error";
                throw new PawapayException("Status check failed: " + errorMsg, status);
            }

            Map<String, Object> data = (Map<String, Object>) raw.get("response");
            TransactionStatus ts = new TransactionStatus();
            if (data != null) {
                ts.setDepositId((String) data.get("depositId"));
                ts.setStatus((String) data.get("status"));
            }
            return ts;
        } catch (PawapayException e) {
            throw e;
        } catch (Exception e) {
            throw new PawapayException("Status check failed: " + e.getMessage());
        }
    }
}