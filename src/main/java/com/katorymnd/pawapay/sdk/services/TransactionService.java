// src/main/java/com/katorymnd/pawapay/sdk/services/TransactionService.java
package com.katorymnd.pawapay.sdk.services;

import com.katorymnd.pawapay.sdk.api.ApiClient;
import com.katorymnd.pawapay.sdk.exceptions.PawapayException;
import com.katorymnd.pawapay.sdk.models.TransactionStatus;

import java.util.Map;

/**
 * Service layer for PawaPay transaction status operations.
 * Handles status checks for deposits, payouts, and refunds.
 */
public class TransactionService {

    private final ApiClient apiClient;

    public TransactionService(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /**
     * Checks the status of any transaction by ID and type.
     * @param transactionId The unique transaction identifier
     * @param type One of: "deposit", "payout", or "refund"
     * @return TransactionStatus with current state
     * @throws PawapayException if API call fails or transaction not found
     */
    @SuppressWarnings("unchecked")
    public TransactionStatus checkStatus(String transactionId, String type) throws PawapayException {
        // Defensive null checks
        if (transactionId == null || transactionId.isEmpty()) {
            throw new PawapayException("Invalid transactionId: cannot be null or empty");
        }
        if (type == null || type.isEmpty()) {
            type = "deposit"; // Default to deposit for backward compatibility
        }

        // Validate type
        if (!type.matches("^(deposit|payout|refund)$")) {
            throw new PawapayException("Invalid transaction type: must be deposit, payout, or refund");
        }

        try {
            Map<String, Object> raw = apiClient.checkTransactionStatusAuto(transactionId, type).join();
            
            int statusCode = (int) raw.get("status");
            
            // Handle 404 specially (transaction not found)
            if (statusCode == 404) {
                throw new PawapayException("Transaction not found: " + transactionId, 404);
            }
            
            // Handle other error codes
            if (statusCode < 200 || statusCode >= 300) {
                Object resp = raw.get("response");
                String errorMsg = resp != null ? resp.toString() : "Unknown API error";
                throw new PawapayException("Status check failed: " + errorMsg, statusCode);
            }

            // Map successful response to typed model
            Map<String, Object> data = (Map<String, Object>) raw.get("response");
            TransactionStatus status = new TransactionStatus();
            if (data != null) {
                // Handle various ID field names the API might return
                String id = (String) data.get("transactionId");
                if (id == null) id = (String) data.get("depositId");
                if (id == null) id = (String) data.get("payoutId");
                if (id == null) id = (String) data.get("refundId");
                
                status.setTransactionId(id);
                status.setDepositId(id); // Alias for compatibility
                status.setStatus((String) data.get("status"));
                status.setType((String) data.get("type"));
                status.setAmount((String) data.get("amount"));
                status.setCurrency((String) data.get("currency"));
                status.setCreatedAt((String) data.get("createdAt"));
                status.setUpdatedAt((String) data.get("updatedAt"));
            }
            return status;
            
        } catch (PawapayException e) {
            throw e;
        } catch (Exception e) {
            throw new PawapayException("Status check failed: " + e.getMessage());
        }
    }

    // Convenience methods for each transaction type
    public TransactionStatus checkDepositStatus(String depositId) throws PawapayException {
        return checkStatus(depositId, "deposit");
    }
    
    public TransactionStatus checkPayoutStatus(String payoutId) throws PawapayException {
        return checkStatus(payoutId, "payout");
    }
    
    public TransactionStatus checkRefundStatus(String refundId) throws PawapayException {
        return checkStatus(refundId, "refund");
    }
}