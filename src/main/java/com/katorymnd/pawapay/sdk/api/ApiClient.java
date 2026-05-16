// src/main/java/com/katorymnd/pawapay/sdk/api/ApiClient.java
package com.katorymnd.pawapay.sdk.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.katorymnd.pawapay.sdk.config.Config;
import com.katorymnd.pawapay.sdk.core.NativeCore;
import com.katorymnd.pawapay.sdk.utils.license.IntegrityChecker;
import com.katorymnd.pawapay.sdk.utils.license.LicenseValidator;
import com.katorymnd.pawapay.sdk.utils.license.ProtectionManager;
import com.katorymnd.pawapay.sdk.utils.license.ServerCheck;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * PawaPay API Client with dual-stack V1 and V2 support
 * Includes comprehensive license protection and integrity checks
 */
public class ApiClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiClient.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    // =========================================================================
    // HONEYPOT STATE - The "Crack Me" Booleans
    // =========================================================================
    public static boolean disableSecurityChecks = false;
    public static boolean isTrialMode = false;
    private boolean forceSslBypass = false;
    // =========================================================================

    // Protection Modules
    private final ProtectionManager protection = ProtectionManager.getInstance();
    private final IntegrityChecker integrity = IntegrityChecker.getInstance();
    private final LicenseValidator validator = LicenseValidator.getInstance();
    private final ServerCheck serverCheck = ServerCheck.getInstance();

    // State
    private boolean isInitializing = true;
    private CompletableFuture<Void> initTask = null;
    private String initializationError = null;
    private boolean licenseValid = false;
    @SuppressWarnings("unused")
    private String license = null;
    @SuppressWarnings("unused")
    private boolean activated = false;

    // Config variables
    private final String apiToken;
    @SuppressWarnings("unused")
    private final String environment;
    private final boolean sslVerify;
    private final String apiVersion;
    private final String apiBaseUrl;

    private HttpClient httpClient;

    /**
     * Initialize the API client
     *
     * @param config      The Core Configuration object
     * @param licenseKey  SDK license key (REQUIRED)
     * @param sslVerify   Enable TLS verification (default: true)
     * @param apiVersion  'v1' or 'v2' (default: 'v1')
     */
    public ApiClient(Config config, String licenseKey, boolean sslVerify, String apiVersion) {
        this.apiToken = config.getApiKey();
        this.environment = config.getEnvironment();
        this.sslVerify = sslVerify;
        
        if ("v2".equalsIgnoreCase(apiVersion)) {
            this.apiVersion = "v2";
        } else {
            this.apiVersion = "v1";
        }

        // Base URL is normalized by the Config object
        this.apiBaseUrl = config.getBaseUrl();

        initializeHttpClient(config.getTimeout());

        if (licenseKey != null && !licenseKey.trim().isEmpty()) {
            this.initTask = initializeLicense(licenseKey);
        } else {
            this.isInitializing = false;
            this.initializationError = "No license key provided";
            this.licenseValid = false;
        }
    }

    private void initializeHttpClient(int timeoutMillis) {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMillis));

        if (!sslVerify || forceSslBypass) {
            try {
                TrustManager[] trustAllCerts = new TrustManager[]{
                        new X509TrustManager() {
                            public X509Certificate[] getAcceptedIssuers() { return null; }
                            public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                            public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                        }
                };
                SSLContext sc = SSLContext.getInstance("TLS");
                sc.init(null, trustAllCerts, new SecureRandom());
                builder.sslContext(sc);
            } catch (Exception e) {
                LOGGER.warn("Failed to set insecure SSL context, falling back to default.", e);
            }
        }

        this.httpClient = builder.build();
    }

    private CompletableFuture<Void> initializeLicense(String licenseKey) {
        return CompletableFuture.runAsync(() -> {
            try {
                // --- HONEYPOT SEQUENCE ---
                if (protection.isDestroyed()) {
                    throw new Exception("SDK has been disabled due to previous violations");
                }

                if (!integrity.verifyAll()) {
                    protection.recordViolation("Code tampering detected");
                    protection.destroy();
                    throw new Exception("SDK integrity check failed - possible tampering detected");
                }

                Map<String, Object> localCheck = validator.validate(licenseKey);
                if (!Boolean.TRUE.equals(localCheck.get("valid"))) {
                    String reason = (String) localCheck.getOrDefault("reason", "Unknown");
                    protection.recordViolation(reason);
                    throw new Exception("License validation failed: " + reason);
                }

                Map<String, Object> serverResult = serverCheck.validateAndActivate(licenseKey).join();
                if (!Boolean.TRUE.equals(serverResult.get("valid"))) {
                    String reason = (String) serverResult.getOrDefault("reason", "Server validation failed");
                    protection.recordViolation(reason);
                    throw new Exception("License validation failed: " + reason);
                }

                                // Initialize the secure vault with license domain and secret
                String licenseDomain = System.getProperty("PAWAPAY_SDK_LICENSE_DOMAIN", 
                    System.getenv().getOrDefault("PAWAPAY_SDK_LICENSE_DOMAIN", ""));
                String licenseSecret = System.getProperty("PAWAPAY_SDK_LICENSE_SECRET", 
                    System.getenv().getOrDefault("PAWAPAY_SDK_LICENSE_SECRET", ""));

                if (!licenseDomain.isEmpty()) {
                    int vaultResult = NativeCore.secureVaultInit(licenseDomain, licenseSecret);
                    LOGGER.info("[PawaPay] Native vault init result: {}", vaultResult);
                }

                // --- SURGICAL STRIKE: The Native Handshake ---
                boolean tampered = disableSecurityChecks || isTrialMode || forceSslBypass;
                int nativeResult = NativeCore.secureInitializeClient(tampered);

                if (nativeResult != 1) {
                    throw new Exception("Native security handshake failed");
                }

                // Warn if license expiring soon
                if (localCheck.containsKey("days_remaining")) {
                    int days = ((Number) localCheck.get("days_remaining")).intValue();
                    if (days < 30) {
                        LOGGER.warn("[PawaPay] License expires in {} days - please renew at https://katorymnd.com", days);
                    }
                }

                // Success
                this.license = licenseKey;
                this.licenseValid = true;
                this.activated = Boolean.TRUE.equals(serverResult.get("activated"));
                LOGGER.info("[PawaPay] License validated successfully");

            } catch (Exception error) {
                LOGGER.error("[PawaPay] License initialization failed: {}", error.getMessage());
                this.licenseValid = false;
                this.initializationError = error.getMessage();
            } finally {
                this.isInitializing = false;
            }
        });
    }

    /**
     * Low level HTTP wrapper bridging standard Java networking with your Protection layer.
     */
    private CompletableFuture<Map<String, Object>> makeApiRequest(String endpoint, String method, Object data) {
        
        // 1. THE WAIT: If startup is still happening, pause here until it finishes.
        // MUST be before native check so Rust's CLIENT_INITIALIZED is true
        if (isInitializing && initTask != null) {
            try {
                initTask.join();
            } catch (Exception ignored) {}
        }
        
        // 2. SECURITY LEVEL 1: Total Destruction (Tampering)
        if (protection.isDestroyed() && !disableSecurityChecks) {
            protection.destroy();
            return failedFuture("SDK disabled - license violation detected");
        }

        // 3. SECURITY LEVEL 2: License Validity
        if (!isInitializing && !licenseValid && !disableSecurityChecks) {
            String errorReason = initializationError != null ? initializationError : "License validation failed";
            return failedFuture("SDK Request Blocked: " + errorReason);
        }
        
        // --- SURGICAL STRIKE: Native Request Verification ---
        // Now runs AFTER license init has completed
        boolean tampered = disableSecurityChecks || isTrialMode || forceSslBypass;
        if (!NativeCore.secureRequestVerification(tampered)) {
            protection.recordViolation("Unauthorized native request attempt");
            return failedFuture("SDK Security Block: Request denied by Native Core.");
        }
        
        // --- PROTECTIVE WRAPPER (Mapped from Python's 5% random method wrap) ---
        if (Math.random() < 0.05) {
            if (!isInitializing) {
                if (protection.isDestroyed() && !disableSecurityChecks) {
                    protection.destroy();
                    return failedFuture("SDK disabled - license violation detected");
                }
                if (!integrity.randomCheck() && !disableSecurityChecks) {
                    protection.recordViolation("Code tampering detected during runtime");
                    protection.destroy();
                    return failedFuture("SDK integrity check failed");
                }
                if (!licenseValid && !disableSecurityChecks) {
                    protection.destroy();
                    String msg = initializationError != null ? initializationError : "License no longer valid";
                    return failedFuture(msg);
                }
            }
        }

        // Ensure endpoint formatting
        if (!endpoint.startsWith("/")) {
            endpoint = "/" + endpoint;
        }

        String url = this.apiBaseUrl + endpoint;

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + this.apiToken)
                .header("Content-Type", "application/json");

        if ("POST".equalsIgnoreCase(method)) {
            try {
                String payloadStr = data != null ? mapper.writeValueAsString(data) : "{}";
                requestBuilder.POST(HttpRequest.BodyPublishers.ofString(payloadStr));
            } catch (JsonProcessingException e) {
                return failedFuture("JSON Serialization Error: " + e.getMessage());
            }
        } else if ("GET".equalsIgnoreCase(method)) {
            if (data instanceof Map && !((Map<?, ?>) data).isEmpty()) {
                StringBuilder query = new StringBuilder("?");
                Map<?, ?> params = (Map<?, ?>) data;
                params.forEach((k, v) -> query.append(k).append("=").append(v).append("&"));
                query.setLength(query.length() - 1);
                url += query.toString();
                requestBuilder.uri(URI.create(url));
            }
            requestBuilder.GET();
        } else {
            return failedFuture("Unsupported HTTP method: " + method);
        }

        return httpClient.sendAsync(requestBuilder.build(), HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("status", response.statusCode());

                    try {
                        String body = response.body();
                        Object responseData = body != null && !body.isEmpty()
                                ? mapper.readValue(body, Object.class)
                                : new HashMap<>();
                        result.put("response", responseData);

                        if (response.statusCode() >= 200 && response.statusCode() < 300) {
                            try {
                                protection.recordSuccess();
                            } catch (Exception e) {
                                LOGGER.warn("[PawaPay] protection.recordSuccess failed: {}", e.getMessage());
                            }
                        } else {
                            throw new RuntimeException("Request failed with status code " + response.statusCode() + ", body: " + body);
                        }
                    } catch (Exception e) {
                        throw new RuntimeException("Request Error: " + e.getMessage(), e);
                    }

                    return result;
                });
    }

    private <T> CompletableFuture<T> failedFuture(String message) {
        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(new Exception(message));
        return future;
    }

    // --- V1 - Initiate Deposit ---
    public CompletableFuture<Map<String, Object>> initiateDeposit(String depositId, String amount, String currency,
                                                                  String correspondent, String payer,
                                                                  String statementDescription, List<Object> metadata) {
        Map<String, Object> data = new HashMap<>();
        data.put("depositId", depositId);
        data.put("amount", amount);
        data.put("currency", currency);
        data.put("correspondent", correspondent);

        Map<String, Object> payerObj = new HashMap<>();
        payerObj.put("type", "MSISDN");
        payerObj.put("address", Collections.singletonMap("value", payer));
        data.put("payer", payerObj);

        data.put("customerTimestamp", Instant.now().toString());
        data.put("statementDescription", statementDescription != null ? statementDescription : "Payment for order");

        if (metadata != null && !metadata.isEmpty()) {
            data.put("metadata", metadata);
        }

        return makeApiRequest("/deposits", "POST", data);
    }

    // --- V2 - Initiate Deposit ---
    public CompletableFuture<Map<String, Object>> initiateDepositV2(String depositId, String amount, String currency,
                                                                    String payerMsisdn, String provider,
                                                                    String customerMessage, String clientReferenceId,
                                                                    String preAuthorisationCode, List<Object> metadata) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("depositId", depositId);
        payload.put("amount", amount);
        payload.put("currency", currency);

        Map<String, Object> payerObj = new HashMap<>();
        payerObj.put("type", "MMO");
        Map<String, String> accountDetails = new HashMap<>();
        accountDetails.put("phoneNumber", payerMsisdn);
        accountDetails.put("provider", provider);
        payerObj.put("accountDetails", accountDetails);
        payload.put("payer", payerObj);

        if (customerMessage != null) payload.put("customerMessage", customerMessage);
        if (clientReferenceId != null) payload.put("clientReferenceId", clientReferenceId);
        if (preAuthorisationCode != null) payload.put("preAuthorisationCode", preAuthorisationCode);
        if (metadata != null && !metadata.isEmpty()) payload.put("metadata", metadata);

        return makeApiRequest("/v2/deposits", "POST", payload);
    }

    // --- Version-aware deposit initiation ---
    public CompletableFuture<Map<String, Object>> initiateDepositAuto(Map<String, Object> args) {
        List<Object> metadata = extractMetadata(args);

        if ("v2".equals(this.apiVersion)) {
            return initiateDepositV2(
                    (String) args.get("depositId"), (String) args.get("amount"), (String) args.get("currency"),
                    (String) args.get("payerMsisdn"), (String) args.get("provider"),
                    (String) args.get("customerMessage"), (String) args.get("clientReferenceId"),
                    (String) args.get("preAuthorisationCode"), metadata
            );
        }

        return initiateDeposit(
                (String) args.get("depositId"), (String) args.get("amount"), (String) args.get("currency"),
                (String) args.get("correspondent"), (String) args.get("payerMsisdn"),
                (String) args.getOrDefault("statementDescription", "Payment for order"), metadata
        );
    }

    // --- V1 - Initiate Payout ---
    public CompletableFuture<Map<String, Object>> initiatePayout(String payoutId, String amount, String currency,
                                                                 String correspondent, String recipient,
                                                                 String statementDescription, List<Object> metadata) {
        Map<String, Object> data = new HashMap<>();
        data.put("payoutId", payoutId);
        data.put("amount", amount);
        data.put("currency", currency);
        data.put("correspondent", correspondent);

        Map<String, Object> recipientObj = new HashMap<>();
        recipientObj.put("type", "MSISDN");
        recipientObj.put("address", Collections.singletonMap("value", recipient));
        data.put("recipient", recipientObj);

        data.put("customerTimestamp", Instant.now().toString());
        data.put("statementDescription", statementDescription != null ? statementDescription : "Payout to customer");

        if (metadata != null && !metadata.isEmpty()) {
            data.put("metadata", metadata);
        }

        return makeApiRequest("/payouts", "POST", data);
    }

    // --- V2 - Initiate Payout ---
    public CompletableFuture<Map<String, Object>> initiatePayoutV2(String payoutId, String amount, String currency,
                                                                   String recipientMsisdn, String provider,
                                                                   String customerMessage, List<Object> metadata) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("payoutId", payoutId);
        payload.put("amount", amount);
        payload.put("currency", currency);

        Map<String, Object> recipientObj = new HashMap<>();
        recipientObj.put("type", "MMO");
        Map<String, String> accountDetails = new HashMap<>();
        accountDetails.put("phoneNumber", recipientMsisdn);
        accountDetails.put("provider", provider);
        recipientObj.put("accountDetails", accountDetails);
        payload.put("recipient", recipientObj);

        if (customerMessage != null) payload.put("customerMessage", customerMessage);
        
        if (metadata != null && !metadata.isEmpty()) {
            payload.put("metadata", normalizeMetadata(metadata));
        }

        return makeApiRequest("/v2/payouts", "POST", payload);
    }

    // --- Version-aware payout initiation ---
    public CompletableFuture<Map<String, Object>> initiatePayoutAuto(Map<String, Object> args) {
        List<Object> metadata = extractMetadata(args);

        if ("v2".equals(this.apiVersion)) {
            return initiatePayoutV2(
                    (String) args.get("payoutId"), (String) args.get("amount"), (String) args.get("currency"),
                    (String) args.get("recipientMsisdn"), (String) args.get("provider"),
                    (String) args.get("customerMessage"), metadata
            );
        }

        return initiatePayout(
                (String) args.get("payoutId"), (String) args.get("amount"), (String) args.get("currency"),
                (String) args.get("correspondent"), (String) args.get("recipientMsisdn"),
                (String) args.getOrDefault("statementDescription", "Payout to customer"), metadata
        );
    }

    // --- V1 - Initiate Refund ---
    public CompletableFuture<Map<String, Object>> initiateRefund(String refundId, String depositId, String amount, List<Object> metadata) {
        Map<String, Object> data = new HashMap<>();
        data.put("refundId", refundId);
        data.put("depositId", depositId);
        data.put("amount", amount);
        if (metadata != null && !metadata.isEmpty()) data.put("metadata", metadata);

        return makeApiRequest("/refunds", "POST", data);
    }

    // --- V2 - Initiate Refund ---
    public CompletableFuture<Map<String, Object>> initiateRefundV2(String refundId, String depositId, String amount,
                                                                   String currency, List<Object> metadata) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("refundId", refundId);
        payload.put("depositId", depositId);
        payload.put("amount", amount);
        payload.put("currency", currency);

        if (metadata != null && !metadata.isEmpty()) {
            payload.put("metadata", normalizeMetadata(metadata));
        }

        return makeApiRequest("/v2/refunds", "POST", payload);
    }

    // --- Version-aware refund initiation ---
    public CompletableFuture<Map<String, Object>> initiateRefundAuto(Map<String, Object> args) {
        List<Object> metadata = extractMetadata(args);

        if ("v2".equals(this.apiVersion)) {
            return initiateRefundV2(
                    (String) args.get("refundId"), (String) args.get("depositId"),
                    (String) args.get("amount"), (String) args.get("currency"), metadata
            );
        }

        return initiateRefund(
                (String) args.get("refundId"), (String) args.get("depositId"),
                (String) args.get("amount"), metadata
        );
    }

    // --- Meta endpoints ---
    public CompletableFuture<Map<String, Object>> checkMnoAvailabilityAuto(String country, String operationType) {
        Map<String, String> params = new HashMap<>();
        if (country != null) params.put("country", country);
        if (operationType != null) params.put("operationType", operationType);

        String endpoint = "v2".equals(this.apiVersion) ? "/v2/availability" : "/availability";
        return makeApiRequest(endpoint, "GET", params.isEmpty() ? null : params);
    }

    public CompletableFuture<Map<String, Object>> checkActiveConfAuto(String country, String operationType) {
        Map<String, String> params = new HashMap<>();
        if (country != null) params.put("country", country);
        if (operationType != null) params.put("operationType", operationType);

        String endpoint = "v2".equals(this.apiVersion) ? "/v2/active-conf" : "/active-conf";
        return makeApiRequest(endpoint, "GET", params.isEmpty() ? null : params);
    }

    // --- Transaction Status ---
    public CompletableFuture<Map<String, Object>> checkTransactionStatusAuto(String transactionId, String type) {
        String t = type != null ? type : "deposit";
        String endpoint;

        if ("v2".equals(this.apiVersion)) {
            if ("payout".equals(t)) endpoint = "/v2/payouts/" + transactionId;
            else if ("refund".equals(t)) endpoint = "/v2/refunds/" + transactionId;
            else if ("remittance".equals(t)) endpoint = "/v2/remittances/" + transactionId;
            else endpoint = "/v2/deposits/" + transactionId;
        } else {
            if ("remittance".equals(t)) return failedFuture("Remittance status is only available in API v2.");
            if ("payout".equals(t)) endpoint = "/payouts/" + transactionId;
            else if ("refund".equals(t)) endpoint = "/refunds/" + transactionId;
            else endpoint = "/deposits/" + transactionId;
        }

        return makeApiRequest(endpoint, "GET", null);
    }

    // --- Payment Page Session ---
    public CompletableFuture<Map<String, Object>> createPaymentPageSessionAuto(Map<String, Object> params) {
        if ("v2".equals(this.apiVersion)) {
            return createPaymentPageSessionV2(params);
        }
        return createPaymentPageSession(params);
    }

    private CompletableFuture<Map<String, Object>> createPaymentPageSession(Map<String, Object> params) {
        String[] required = {"depositId", "returnUrl", "statementDescription"};
        for (String req : required) {
            if (!params.containsKey(req) || params.get(req) == null) {
                return failedFuture("Missing required parameter: " + req);
            }
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("depositId", String.valueOf(params.get("depositId")));
        payload.put("returnUrl", String.valueOf(params.get("returnUrl")));
        payload.put("statementDescription", String.valueOf(params.get("statementDescription")));
        payload.put("language", params.getOrDefault("language", "EN"));

        String[] optional = {"amount", "msisdn", "country", "reason"};
        for (String opt : optional) {
            if (params.containsKey(opt) && params.get(opt) != null) {
                payload.put(opt, params.get(opt));
            }
        }

        List<Object> metadata = extractMetadata(params);
        if (!metadata.isEmpty()) payload.put("metadata", metadata);

        return makeApiRequest("/v1/widget/sessions", "POST", payload);
    }

    private CompletableFuture<Map<String, Object>> createPaymentPageSessionV2(Map<String, Object> params) {
        String[] required = {"depositId", "returnUrl"};
        for (String req : required) {
            if (!params.containsKey(req) || params.get(req) == null) {
                return failedFuture("Missing required parameter: " + req);
            }
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("depositId", String.valueOf(params.get("depositId")));
        payload.put("returnUrl", String.valueOf(params.get("returnUrl")));

        if (params.containsKey("customerMessage")) {
            payload.put("customerMessage", String.valueOf(params.get("customerMessage")));
        } else if (params.containsKey("statementDescription")) {
            payload.put("customerMessage", String.valueOf(params.get("statementDescription")));
        }

        if (params.containsKey("amountDetails") && params.get("amountDetails") instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> ad = (Map<String, Object>) params.get("amountDetails");
            if (ad.containsKey("amount") && ad.containsKey("currency")) {
                Map<String, String> adStr = new HashMap<>();
                adStr.put("amount", String.valueOf(ad.get("amount")));
                adStr.put("currency", String.valueOf(ad.get("currency")));
                payload.put("amountDetails", adStr);
            }
        } else if (params.containsKey("amount") && params.containsKey("currency")) {
            Map<String, String> adStr = new HashMap<>();
            adStr.put("amount", String.valueOf(params.get("amount")));
            adStr.put("currency", String.valueOf(params.get("currency")));
            payload.put("amountDetails", adStr);
        }

        if (params.containsKey("phoneNumber")) {
            payload.put("phoneNumber", String.valueOf(params.get("phoneNumber")).replaceAll("\\D", ""));
        } else if (params.containsKey("msisdn")) {
            payload.put("phoneNumber", String.valueOf(params.get("msisdn")).replaceAll("\\D", ""));
        }

        String[] optional = {"language", "country", "reason"};
        for (String opt : optional) {
            if (params.containsKey(opt) && params.get(opt) != null) {
                payload.put(opt, String.valueOf(params.get(opt)));
            }
        }

        List<Object> metadata = extractMetadata(params);
        if (!metadata.isEmpty()) {
            payload.put("metadata", normalizeMetadata(metadata));
        }

        return makeApiRequest("/v2/paymentpage", "POST", payload);
    }

    // --- Utilities ---
    @SuppressWarnings("unchecked")
    private List<Object> extractMetadata(Map<String, Object> args) {
        if (args.containsKey("metadata") && args.get("metadata") instanceof List) {
            return (List<Object>) args.get("metadata");
        }
        return new ArrayList<>();
    }

    private List<Object> normalizeMetadata(List<Object> metadata) {
        List<Object> norm = new ArrayList<>();
        for (Object item : metadata) {
            if (item instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> mapItem = (Map<String, Object>) item;
                if (mapItem.containsKey("fieldName") && mapItem.containsKey("fieldValue")) {
                    Map<String, Object> obj = new HashMap<>();
                    obj.put(String.valueOf(mapItem.get("fieldName")), mapItem.get("fieldValue"));
                    if (mapItem.containsKey("isPII")) {
                        obj.put("isPII", Boolean.valueOf(String.valueOf(mapItem.get("isPII"))));
                    }
                    norm.add(obj);
                } else {
                    norm.add(item);
                }
            } else {
                norm.add(item);
            }
        }
        return norm;
    }

    /**
     * Clean up SDK resources and stop background monitoring threads.
     */
    public void close() {
        if (protection != null) {
            protection.shutdown();
        }
        LOGGER.info("ApiClient closed and background tasks terminated.");
    }
}