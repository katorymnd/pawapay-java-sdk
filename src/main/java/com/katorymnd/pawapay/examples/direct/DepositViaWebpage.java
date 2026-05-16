package com.katorymnd.pawapay.examples.direct;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Java SDK for PawaPay Payment Page (V2) and Widget (V1)
 */
public class DepositViaWebpage {

    // Configuration
    private static final Map<String, Map<String, String>> CONFIG = new HashMap<>() {{
        put("sandbox", new HashMap<>() {{
            put("api_url", "https://api.sandbox.pawapay.io");
            put("api_token", "eyJraWQiOiIxIiwiYWxnIjoiRVMyNTYifQ.eyJ0dCI6IkFBVCIsInN1YiI6IjI3OTQiLCJleHAiOjIwNDIyMDIxMzQsImlhdCI6MTcyNjY2OTMzNCwicG0iOiJEQUYsUEFGIiwianRpIjoiODZhZWYzNjEtMGYzNC00OWE2LWFiYjktODI3YTZmMGI2NGNjIn0.WsmEqwRgRlgPyCCDA2HcHVAjL7gqIAksuX7BL_XQxiEXLj9qk0SiNIbIa3wczac7LKy211-Vmp_9zUX3G1Qn-Q");
        }});
        put("production", new HashMap<>() {{
            put("api_url", "https://api.pawapay.io");
            put("api_token", "your_production_api_token");
        }});
    }};

    private static final OkHttpClient client;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    static {
        // SSL Context - Disable SSL verification for development 
        // WARNING: This is for development only. In production, use proper SSL verification
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS);

        // Disable SSL certificate verification 
        // For production, remove this and use proper SSL verification
        try {
            javax.net.ssl.TrustManager[] trustAllCerts = new javax.net.ssl.TrustManager[]{
                new javax.net.ssl.X509TrustManager() {
                    @Override
                    public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) {}
                    @Override
                    public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) {}
                    @Override
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() { return new java.security.cert.X509Certificate[]{}; }
                }
            };
            javax.net.ssl.SSLContext sslContext = javax.net.ssl.SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            builder.sslSocketFactory(sslContext.getSocketFactory(), (javax.net.ssl.X509TrustManager) trustAllCerts[0]);
            builder.hostnameVerifier((hostname, session) -> true);
        } catch (Exception e) {
            e.printStackTrace();
        }

        client = builder.build();
    }

    /**
     * Generate UUID v4
     */
    public static String generateUuidV4() {
        return UUID.randomUUID().toString();
    }

    /**
     * Sanitize message (remove special characters)
     */
    private static String sanitizeMessage(String message) {
        if (message == null) return null;
        return message.replaceAll("[^a-zA-Z0-9 ]", "");
    }

    /**
     * Convert V1-style metadata array to V2 format
     */
    public static ArrayNode mapMetadataV1ToV2(List<Map<String, Object>> metadataV1) {
        ArrayNode mappedArray = objectMapper.createArrayNode();
        
        if (metadataV1 == null || metadataV1.isEmpty()) {
            return mappedArray;
        }

        for (Map<String, Object> item : metadataV1) {
            if (item.containsKey("fieldName") && item.containsKey("fieldValue")) {
                ObjectNode newItem = objectMapper.createObjectNode();
                newItem.put((String) item.get("fieldName"), (String) item.get("fieldValue"));
                if (item.containsKey("isPII") && item.get("isPII") instanceof Boolean) {
                    newItem.put("isPII", (Boolean) item.get("isPII"));
                }
                mappedArray.add(newItem);
            }
        }
        return mappedArray;
    }

    /**
     * Create payment session
     */
    public static Map<String, Object> createPaymentSession(
            String environment,
            String apiVersion,
            String returnUrl,
            String amount,
            String currency,
            String msisdn,
            String customerMessage,
            String language,
            String country,
            String reason,
            List<Map<String, Object>> metadata) throws IOException {

        // --- SURGICAL FIX: Sanitize Message ---
        if (customerMessage != null) {
            customerMessage = sanitizeMessage(customerMessage);
        }

        // Validate environment
        if (!CONFIG.containsKey(environment)) {
            throw new IllegalArgumentException("Invalid environment: " + environment);
        }

        String apiBaseUrl = CONFIG.get(environment).get("api_url");
        String apiToken = CONFIG.get(environment).get("api_token");

        // Resolve Endpoint
        String endpoint = apiVersion.equals("v2") ? "/v2/paymentpage" : "/v1/widget/sessions";
        String apiUrl = apiBaseUrl + endpoint;

        // Generate ID
        String depositId = generateUuidV4();

        // Build Payload
        ObjectNode payload = objectMapper.createObjectNode();

        if (apiVersion.equals("v2")) {
            // V2 Payload
            payload.put("depositId", depositId);
            payload.put("returnUrl", returnUrl);
            payload.put("customerMessage", customerMessage);
            
            ObjectNode amountDetails = objectMapper.createObjectNode();
            amountDetails.put("amount", amount);
            amountDetails.put("currency", currency);
            payload.set("amountDetails", amountDetails);
            
            payload.put("language", language);
            payload.put("reason", reason);
            payload.set("metadata", mapMetadataV1ToV2(metadata));
            
            if (msisdn != null && !msisdn.isEmpty()) {
                payload.put("phoneNumber", msisdn);
            }
            if (country != null && !country.isEmpty()) {
                payload.put("country", country);
            }
        } else {
            // V1 Payload
            payload.put("depositId", depositId);
            payload.put("returnUrl", returnUrl);
            payload.put("statementDescription", customerMessage); // Map V2 name to V1 field
            payload.put("amount", amount);
            payload.put("language", language);
            payload.put("reason", reason);
            
            // Convert metadata for V1 (FIXED)
            if (metadata != null && !metadata.isEmpty()) {
                ArrayNode metadataArray = objectMapper.createArrayNode();
                for (Map<String, Object> item : metadata) {
                    ObjectNode metadataItem = objectMapper.createObjectNode();
                    for (Map.Entry<String, Object> entry : item.entrySet()) {
                        Object val = entry.getValue();
                        if (val instanceof String) {
                            metadataItem.put(entry.getKey(), (String) val);
                        } else if (val instanceof Boolean) {
                            metadataItem.put(entry.getKey(), (Boolean) val);
                        } else if (val instanceof Integer) {
                            metadataItem.put(entry.getKey(), (Integer) val);
                        } else if (val instanceof Long) {
                            metadataItem.put(entry.getKey(), (Long) val);
                        } else if (val instanceof Double) {
                            metadataItem.put(entry.getKey(), (Double) val);
                        } else if (val instanceof Number) {
                            // Catch-all for other Number types like Float or BigDecimal
                            metadataItem.put(entry.getKey(), ((Number) val).doubleValue());
                        }
                    }
                    metadataArray.add(metadataItem);
                }
                payload.set("metadata", metadataArray);
            }
            
            if (msisdn != null && !msisdn.isEmpty()) {
                payload.put("msisdn", msisdn);
            }
            if (country != null && !country.isEmpty()) {
                payload.put("country", country);
            }
        }

        // Make API Request
        RequestBody body = RequestBody.create(
                payload.toString(),
                MediaType.parse("application/json")
        );

        Request request = new Request.Builder()
                .url(apiUrl)
                .post(body)
                .addHeader("Authorization", "Bearer " + apiToken)
                .addHeader("Content-Type", "application/json")
                .build();

        try (Response response = client.newCall(request).execute()) {
            Map<String, Object> result = new HashMap<>();
            
            if (!response.isSuccessful()) {
                String responseBody = response.body() != null ? response.body().string() : "";
                Map<String, Object> error = new HashMap<>();
                error.put("status", response.code());
                error.put("message", "Failed to create payment session (" + apiVersion + ")");
                error.put("responseData", responseBody);
                result.put("error", error);
                result.put("success", false);
                return result;
            }

            String responseBody = response.body() != null ? response.body().string() : "";
            JsonNode jsonResponse = objectMapper.readTree(responseBody);
            
            result.put("success", true);
            result.put("depositId", depositId);
            result.put("apiVersion", apiVersion);
            result.put("environment", environment);
            result.put("response", objectMapper.readValue(responseBody, Map.class));
            result.put("redirectUrl", jsonResponse.has("redirectUrl") ? 
                    jsonResponse.get("redirectUrl").asText() : 
                    (jsonResponse.has("url") ? jsonResponse.get("url").asText() : null));
            result.put("statusCode", response.code());
            
            return result;
        }
    }

    /**
     * Wrapper method for direct deposit via webpage
     */
    public static Map<String, Object> directDepositViaWebpage(
            String environment,
            String apiVersion,
            String returnUrl,
            String amount,
            String currency,
            String msisdn,
            String customerMessage,
            String language,
            String country,
            String reason,
            List<Map<String, Object>> metadata) throws IOException {
        
        return createPaymentSession(
                environment, apiVersion, returnUrl, amount, currency, 
                msisdn, customerMessage, language, country, reason, metadata);
    }

    /**
     * Main method for CLI usage
     */
    public static void main(String[] args) {
        String apiVersion = "v1";
        String amount = "1000";
        
        // Parse command line arguments
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--version":
                case "-v":
                    if (i + 1 < args.length) apiVersion = args[i + 1];
                    break;
                case "--amount":
                case "-a":
                    if (i + 1 < args.length) amount = args[i + 1];
                    break;
            }
        }

        try {
            Map<String, Object> result = directDepositViaWebpage(
                    "sandbox",
                    apiVersion,
                    "https://example.com/paymentProcessed",
                    amount,
                    "UGX",
                    null,
                    "Payment",
                    "EN",
                    "UGA",
                    "Payment",
                    null
            );

            if (result.containsKey("error")) {
                System.out.println("ERROR: " + result.get("error"));
            } else {
                System.out.println("SUCCESS: " + objectMapper.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(result));
            }
        } catch (Exception e) {
            System.out.println("ERROR: " + e.getMessage());
            e.printStackTrace();
        }

        // Force the JVM to close, killing OkHttp's lingering background threads
        System.exit(0);
    }
}