// src/main/java/com/katorymnd/pawapay/sdk/utils/FailureCodeHelper.java
package com.katorymnd.pawapay.sdk.utils;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Failure Code Helper
 * Maps and explains PawaPay error codes
 */
public class FailureCodeHelper {
    
    private FailureCodeHelper() {
        // Private constructor to prevent instantiation
    }
    
    /**
     * Aliases to normalize code names across V1/V2 and operations.
     */
    private static final Map<String, String> ALIASES;
    
    /**
     * Rejection messages (initiation time) — V1 + V2
     */
    private static final Map<String, String> REJECTION_MESSAGES;
    
    /**
     * Failure messages (processing-time) — V1 + V2 + Remittances
     */
    private static final Map<String, String> FAILURE_MESSAGES;
    
    /**
     * Status messages & terminality — V1 + V2
     */
    private static final Map<String, String> STATUS_MESSAGES;
    
    /**
     * Final statuses (terminal states)
     */
    private static final Map<String, Boolean> FINAL_STATUSES;
    
    static {
        // Initialize aliases
        Map<String, String> aliases = new HashMap<>();
        // Provider/Correspondent synonyms
        aliases.put("CORRESPONDENT_TEMPORARILY_UNAVAILABLE", "PROVIDER_TEMPORARILY_UNAVAILABLE");
        // Amount bounds synonyms
        aliases.put("AMOUNT_TOO_SMALL", "AMOUNT_OUT_OF_BOUNDS");
        aliases.put("AMOUNT_TOO_LARGE", "AMOUNT_OUT_OF_BOUNDS");
        // Phone number / format synonyms
        aliases.put("INVALID_RECIPIENT_FORMAT", "INVALID_PHONE_NUMBER");
        aliases.put("INVALID_PAYER_FORMAT", "INVALID_PHONE_NUMBER");
        // Balance synonyms
        aliases.put("BALANCE_INSUFFICIENT", "PAWAPAY_WALLET_OUT_OF_FUNDS");
        // Flow synonyms (already-in-process)
        aliases.put("TRANSACTION_ALREADY_IN_PROCESS", "PAYMENT_IN_PROGRESS");
        // Recipient allowed / wallet limits
        aliases.put("RECIPIENT_NOT_ALLOWED_TO_RECEIVE", "WALLET_LIMIT_REACHED");
        // "Not found" mapping across ops
        aliases.put("DEPOSIT_NOT_FOUND", "NOT_FOUND");
        // Generic other error
        aliases.put("OTHER_ERROR", "UNKNOWN_ERROR");
        ALIASES = Collections.unmodifiableMap(aliases);
        
        // Initialize rejection messages
        Map<String, String> rejectionMessages = new HashMap<>();
        // Common transport/auth/signature (V2)
        rejectionMessages.put("NO_AUTHENTICATION", "Authentication header is missing.");
        rejectionMessages.put("AUTHENTICATION_ERROR", "The API token is invalid.");
        rejectionMessages.put("AUTHORISATION_ERROR", "The API token is not authorised for this request.");
        rejectionMessages.put("HTTP_SIGNATURE_ERROR", "The HTTP signature failed verification.");
        rejectionMessages.put("INVALID_INPUT", "We could not parse the request payload.");
        rejectionMessages.put("MISSING_PARAMETER", "A required parameter is missing.");
        rejectionMessages.put("UNSUPPORTED_PARAMETER", "An unsupported parameter was provided.");
        rejectionMessages.put("INVALID_PARAMETER", "A parameter contains an invalid value.");
        rejectionMessages.put("DUPLICATE_METADATA_FIELD", "Duplicate field in metadata.");
        // Amount/currency/provider/country
        rejectionMessages.put("INVALID_AMOUNT", "The amount is not valid for this provider.");
        rejectionMessages.put("AMOUNT_OUT_OF_BOUNDS", "The amount is outside provider limits.");
        rejectionMessages.put("INVALID_CURRENCY", "The currency is not supported by this provider.");
        rejectionMessages.put("INVALID_COUNTRY", "The specified country is not supported.");
        rejectionMessages.put("INVALID_PROVIDER", "The provider is invalid for this request.");
        rejectionMessages.put("INVALID_PHONE_NUMBER", "The phone number format is invalid.");
        // Business enablement
        rejectionMessages.put("DEPOSITS_NOT_ALLOWED", "Deposits are not enabled for this provider on your account.");
        rejectionMessages.put("PAYOUTS_NOT_ALLOWED", "Payouts are not enabled for this provider on your account.");
        rejectionMessages.put("REFUNDS_NOT_ALLOWED", "Refunds are not enabled for this provider on your account.");
        rejectionMessages.put("REMITTANCES_NOT_ALLOWED", "Remittances are not enabled for this provider on your account.");
        // Availability
        rejectionMessages.put("PROVIDER_TEMPORARILY_UNAVAILABLE", "The provider is temporarily unavailable. Please try again later.");
        // V1-only names (aliased above, but keep messages for clarity)
        rejectionMessages.put("INVALID_PAYER_FORMAT", "The payer phone number format is invalid.");
        rejectionMessages.put("INVALID_RECIPIENT_FORMAT", "The recipient phone number format is invalid.");
        rejectionMessages.put("INVALID_CORRESPONDENT", "The specified correspondent is not supported.");
        rejectionMessages.put("AMOUNT_TOO_SMALL", "The amount is below the minimum.");
        rejectionMessages.put("AMOUNT_TOO_LARGE", "The amount is above the maximum.");
        rejectionMessages.put("CORRESPONDENT_TEMPORARILY_UNAVAILABLE", "The MMO (correspondent) is temporarily unavailable.");
        // Refund-specific V1
        rejectionMessages.put("DEPOSIT_NOT_COMPLETED", "The referenced deposit was not completed.");
        rejectionMessages.put("ALREADY_REFUNDED", "The referenced deposit has already been refunded.");
        rejectionMessages.put("IN_PROGRESS", "Another refund transaction is already in progress.");
        rejectionMessages.put("DEPOSIT_NOT_FOUND", "The referenced deposit was not found.");
        // Refund-specific V2
        rejectionMessages.put("NOT_FOUND", "The referenced deposit was not found.");
        rejectionMessages.put("INVALID_STATE", "The deposit is not in a refundable state (or already refunded).");
        // Wallet balance (initiation level)
        rejectionMessages.put("PAWAPAY_WALLET_OUT_OF_FUNDS", "Your pawaPay wallet does not have sufficient funds.");
        // Generic
        rejectionMessages.put("UNKNOWN_ERROR", "An unknown error occurred while processing the request.");
        REJECTION_MESSAGES = Collections.unmodifiableMap(rejectionMessages);
        
        // Initialize failure messages
        Map<String, String> failureMessages = new HashMap<>();
        // Deposits (V1 & V2)
        failureMessages.put("PAYER_NOT_FOUND", "The phone number does not belong to the specified provider.");
        failureMessages.put("PAYMENT_NOT_APPROVED", "The customer did not approve the payment.");
        failureMessages.put("PAYER_LIMIT_REACHED", "The customer has reached a wallet transaction limit.");
        failureMessages.put("PAYMENT_IN_PROGRESS", "The customer already has a payment pending.");
        failureMessages.put("INSUFFICIENT_BALANCE", "The customer does not have enough funds.");
        failureMessages.put("UNSPECIFIED_FAILURE", "The provider reported a failure without a reason.");
        failureMessages.put("UNKNOWN_ERROR", "An unknown error occurred.");
        // V1 alias kept for backward compat
        failureMessages.put("TRANSACTION_ALREADY_IN_PROCESS", "A previous transaction is still being processed.");
        // Payouts (V1 & V2)
        failureMessages.put("PAWAPAY_WALLET_OUT_OF_FUNDS", "Your pawaPay wallet does not have sufficient funds.");
        failureMessages.put("BALANCE_INSUFFICIENT", "Your pawaPay wallet does not have sufficient funds.");
        failureMessages.put("RECIPIENT_NOT_FOUND", "The phone number does not belong to the specified provider.");
        failureMessages.put("RECIPIENT_NOT_ALLOWED_TO_RECEIVE", "The recipient is temporarily not allowed to receive funds.");
        failureMessages.put("MANUALLY_CANCELLED", "The payout was cancelled while in queue.");
        // Remittances (V2 naming)
        failureMessages.put("WALLET_LIMIT_REACHED", "The recipient has reached a wallet limit.");
        // Friendly fallbacks
        failureMessages.put("OTHER_ERROR", "An unspecified error occurred while processing the transaction.");
        // Non-standard but used in your app
        failureMessages.put("NO_CALLBACK", "The transaction is pending. Please check the status again shortly.");
        FAILURE_MESSAGES = Collections.unmodifiableMap(failureMessages);
        
        // Initialize status messages
        Map<String, String> statusMessages = new HashMap<>();
        statusMessages.put("ACCEPTED", "Accepted for processing.");
        statusMessages.put("ENQUEUED", "Accepted and queued for later processing.");
        statusMessages.put("SUBMITTED", "Submitted to the provider.");
        statusMessages.put("PROCESSING", "Processing with the provider.");
        statusMessages.put("IN_RECONCILIATION", "Being reconciled to determine final status.");
        statusMessages.put("COMPLETED", "Successfully completed.");
        statusMessages.put("FAILED", "Processed but failed.");
        // V2 "wrapper" statuses for GET /v2/.../{id}
        statusMessages.put("FOUND", "Found.");
        statusMessages.put("NOT_FOUND", "Not found.");
        // Some V1 payloads use these text keys within responses
        statusMessages.put("REJECTED", "Rejected at initiation.");
        statusMessages.put("DUPLICATE_IGNORED", "Duplicate of an already accepted request; ignored.");
        STATUS_MESSAGES = Collections.unmodifiableMap(statusMessages);
        
        // Initialize final statuses
        Map<String, Boolean> finalStatuses = new HashMap<>();
        finalStatuses.put("COMPLETED", true);
        finalStatuses.put("FAILED", true);
        // "FOUND/NOT_FOUND" are wrapper statuses, not payment lifecycle; treat as final for the lookup call.
        finalStatuses.put("FOUND", true);
        finalStatuses.put("NOT_FOUND", true);
        FINAL_STATUSES = Collections.unmodifiableMap(finalStatuses);
    }
    
    /**
     * Returns a friendly message for a processing failure code.
     *
     * @param failureCode The failure code
     * @return Friendly error message
     */
    public static String getFailureMessage(String failureCode) {
        String code = normalizeCode(failureCode);
        
        if (FAILURE_MESSAGES.containsKey(code)) {
            return FAILURE_MESSAGES.get(code);
        }
        
        // Check in rejections too (some callers may mix)
        if (REJECTION_MESSAGES.containsKey(code)) {
            return REJECTION_MESSAGES.get(code);
        }
        
        return String.format("An unknown error occurred (Code: %s). Please contact support.", code);
    }
    
    /**
     * Friendly message for initiation rejection codes.
     *
     * @param rejectionCode The rejection code
     * @return Friendly error message
     */
    public static String getRejectionMessage(String rejectionCode) {
        String code = normalizeCode(rejectionCode);
        
        if (REJECTION_MESSAGES.containsKey(code)) {
            return REJECTION_MESSAGES.get(code);
        }
        
        // Check in failures too (defensive)
        if (FAILURE_MESSAGES.containsKey(code)) {
            return FAILURE_MESSAGES.get(code);
        }
        
        return String.format("Your request was rejected (Code: %s). Please review the parameters or try again later.", code);
    }
    
    /**
     * Friendly message for a lifecycle/status string (ACCEPTED, COMPLETED, FAILED, etc.)
     *
     * @param status The status code
     * @return Friendly status message
     */
    public static String getStatusMessage(String status) {
        String s = status != null ? status.toUpperCase().trim() : "";
        return STATUS_MESSAGES.getOrDefault(s, s);
    }
    
    /**
     * Whether a status is terminal (no more state changes expected).
     *
     * @param status The status code
     * @return True if status is final
     */
    public static boolean isFinalStatus(String status) {
        String s = status != null ? status.toUpperCase().trim() : "";
        return FINAL_STATUSES.getOrDefault(s, false);
    }
    
    /**
     * Normalize incoming code:
     * - uppercase/trim
     * - alias to canonical if known
     *
     * @param code The code to normalize
     * @return Normalized code
     */
    public static String normalizeCode(String code) {
        String c = code != null ? code.toUpperCase().trim() : "";
        return ALIASES.getOrDefault(c, c);
    }
}