// src/main/java/com/katorymnd/pawapay/sdk/services/RefundService.java
package com.katorymnd.pawapay.sdk.services;

import com.katorymnd.pawapay.sdk.api.ApiClient;
import com.katorymnd.pawapay.sdk.exceptions.PawapayException;
import com.katorymnd.pawapay.sdk.models.RefundRequest;
import com.katorymnd.pawapay.sdk.models.RefundResponse;
import com.katorymnd.pawapay.sdk.models.TransactionStatus;

import java.util.List;
import java.util.Map;

/**
 * Service layer for PawaPay Refund operations.
 */
public class RefundService {

    private final ApiClient apiClient;

    public RefundService(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    @SuppressWarnings("unchecked")
    public RefundResponse initiateRefund(RefundRequest request) throws PawapayException {
        // Defensive null checks
        if (request == null || request.getRefundId() == null || request.getDepositId() == null) {
            throw new PawapayException("Invalid refund request: missing refundId or depositId");
        }

        try {
            // Call V2 API (requires currency)
            Map<String, Object> raw = apiClient.initiateRefundV2(
                request.getRefundId(),
                request.getDepositId(),
                request.getAmount(),
                request.getCurrency() != null ? request.getCurrency() : "UGX",
                request.getMetadata() != null ? request.getMetadata() : List.of()
            ).join();

            int status = (int) raw.get("status");
            if (status < 200 || status >= 300) {
                Object resp = raw.get("response");
                String errorMsg = resp != null ? resp.toString() : "Unknown API error";
                throw new PawapayException("API error: " + errorMsg, status);
            }

            Map<String, Object> data = (Map<String, Object>) raw.get("response");
            RefundResponse resp = new RefundResponse();
            if (data != null) {
                resp.setRefundId((String) data.get("refundId"));
                resp.setDepositId((String) data.get("depositId"));
                resp.setStatus((String) data.get("status"));
                resp.setNextStep((String) data.get("nextStep"));
            }
            return resp;
        } catch (PawapayException e) {
            throw e;
        } catch (Exception e) {
            throw new PawapayException("Refund failed: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    public TransactionStatus getRefundStatus(String refundId) throws PawapayException {
        if (refundId == null || refundId.isEmpty()) {
            throw new PawapayException("Invalid refundId: cannot be null or empty");
        }

        try {
            Map<String, Object> raw = apiClient.checkTransactionStatusAuto(refundId, "refund").join();
            
            int status = (int) raw.get("status");
            if (status < 200 || status >= 300) {
                Object resp = raw.get("response");
                String errorMsg = resp != null ? resp.toString() : "Unknown API error";
                throw new PawapayException("Status check failed: " + errorMsg, status);
            }

            Map<String, Object> data = (Map<String, Object>) raw.get("response");
            TransactionStatus ts = new TransactionStatus();
            if (data != null) {
                ts.setDepositId((String) data.get("refundId")); // Reuses field for refundId
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