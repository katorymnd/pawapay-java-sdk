// src/main/java/com/katorymnd/pawapay/sdk/services/PayoutService.java
package com.katorymnd.pawapay.sdk.services;

import com.katorymnd.pawapay.sdk.api.ApiClient;
import com.katorymnd.pawapay.sdk.exceptions.PawapayException;
import com.katorymnd.pawapay.sdk.models.PayoutRequest;
import com.katorymnd.pawapay.sdk.models.PayoutResponse;
import com.katorymnd.pawapay.sdk.models.TransactionStatus;

import java.util.List;
import java.util.Map;

public class PayoutService {

    private final ApiClient apiClient;

    public PayoutService(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    @SuppressWarnings("unchecked")
    public PayoutResponse initiatePayout(PayoutRequest request) throws PawapayException {
        // Defensive null checks
        if (request == null || request.getAmount() == null || request.getRecipient() == null) {
            throw new PawapayException("Invalid payout request: missing amount or recipient");
        }

        try {
            // Call V2 API (adapt to V1 if needed based on config)
            Map<String, Object> raw = apiClient.initiatePayoutV2(
                null, // payoutId - let API generate
                request.getAmount().getAmount(),
                request.getAmount().getCurrency(),
                request.getRecipient().getPartyId(),      // msisdn
                request.getRecipient().getProvider(),     // provider
                request.getCustomerMessage(),
                request.getMetadata() != null ? request.getMetadata() : List.of()
            ).join();

            int status = (int) raw.get("status");
            if (status < 200 || status >= 300) {
                Object resp = raw.get("response");
                String errorMsg = resp != null ? resp.toString() : "Unknown API error";
                throw new PawapayException("API error: " + errorMsg, status);
            }

            Map<String, Object> data = (Map<String, Object>) raw.get("response");
            PayoutResponse resp = new PayoutResponse();
            if (data != null) {
                resp.setPayoutId((String) data.get("payoutId"));
                resp.setStatus((String) data.get("status"));
                resp.setNextStep((String) data.get("nextStep"));
            }
            return resp;
        } catch (PawapayException e) {
            throw e;
        } catch (Exception e) {
            throw new PawapayException("Payout failed: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    public TransactionStatus getPayoutStatus(String payoutId) throws PawapayException {
        if (payoutId == null || payoutId.isEmpty()) {
            throw new PawapayException("Invalid payoutId: cannot be null or empty");
        }

        try {
            Map<String, Object> raw = apiClient.checkTransactionStatusAuto(payoutId, "payout").join();
            
            int status = (int) raw.get("status");
            if (status < 200 || status >= 300) {
                Object resp = raw.get("response");
                String errorMsg = resp != null ? resp.toString() : "Unknown API error";
                throw new PawapayException("Status check failed: " + errorMsg, status);
            }

            Map<String, Object> data = (Map<String, Object>) raw.get("response");
            TransactionStatus ts = new TransactionStatus();
            if (data != null) {
                ts.setDepositId((String) data.get("payoutId")); // Reuses field
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