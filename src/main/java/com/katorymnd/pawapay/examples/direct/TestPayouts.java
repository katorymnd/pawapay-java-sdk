package com.katorymnd.pawapay.examples.direct;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.*;

import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Java SDK for PawaPay API - Direct payout testing
 */
public class TestPayouts {

    // Configuration - Same as Deposit
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
    private static final ObjectMapper objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    static {
        // SSL Context - Disable SSL verification for development
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS);

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

    public static String generateUuidV4() {
        return UUID.randomUUID().toString();
    }

    public static Map<String, Object> testPayout(
            String environment, 
            String apiVersion, 
            String amount, 
            String currency,
            String msisdn, 
            String customerMessage, 
            String clientReferenceId) throws IOException {

        // SURGICAL FIX: Sanitize Message
        if (customerMessage != null) {
            customerMessage = customerMessage.replaceAll("[^a-zA-Z0-9 ]", "");
        }

        if (!CONFIG.containsKey(environment)) {
            throw new IllegalArgumentException("Invalid environment: " + environment);
        }

        String apiBaseUrl = CONFIG.get(environment).get("api_url");
        String apiToken = CONFIG.get(environment).get("api_token");

        // Build endpoint
        String endpoint = "v2".equals(apiVersion) ? "/v2/payouts" : "/payouts";
        String apiUrl = apiBaseUrl + endpoint;

        String payoutId = generateUuidV4();
        ObjectNode payload = objectMapper.createObjectNode();

        // Constants
        String countryAlpha3 = "UGA";
        String v1Correspondent = "MTN_MOMO_UGA";
        String v2Provider = "MTN_MOMO_UGA";

        if ("v2".equals(apiVersion)) {
            // V2 Payload
            payload.put("payoutId", payoutId);
            
            ObjectNode recipient = payload.putObject("recipient");
            recipient.put("type", "MMO");
            ObjectNode accountDetails = recipient.putObject("accountDetails");
            accountDetails.put("phoneNumber", msisdn);
            accountDetails.put("provider", v2Provider);
            
            payload.put("customerMessage", customerMessage);
            payload.put("amount", amount);
            payload.put("currency", currency);
            
            // V2 Metadata format
            ArrayNode metadata = payload.putArray("metadata");
            
            ObjectNode orderItem = metadata.addObject();
            orderItem.put("orderId", "ORD-123456");
            
            ObjectNode customerItem = metadata.addObject();
            customerItem.put("customerId", "customer@email.com");
            customerItem.put("isPII", true);
            
        } else {
            // V1 Payload
            payload.put("payoutId", payoutId);
            payload.put("amount", amount);
            payload.put("currency", currency);
            payload.put("country", countryAlpha3);
            payload.put("correspondent", v1Correspondent);
            
            ObjectNode recipient = payload.putObject("recipient");
            recipient.put("type", "MSISDN");
            recipient.putObject("address").put("value", msisdn);
            
            // Format timestamp as ISO 8601 with Z suffix
            String timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
            payload.put("customerTimestamp", timestamp);
            
            payload.put("statementDescription", customerMessage);
            
            // V1 Metadata format
            ArrayNode metadata = payload.putArray("metadata");
            
            ObjectNode orderItem = metadata.addObject();
            orderItem.put("fieldName", "orderId");
            orderItem.put("fieldValue", "ORD-123456");
            
            ObjectNode customerItem = metadata.addObject();
            customerItem.put("fieldName", "customerId");
            customerItem.put("fieldValue", "customer@email.com");
            customerItem.put("isPII", true);
        }

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
            String responseBody = response.body() != null ? response.body().string() : "";
            
            // Check for success codes (200 or 201)
            if (response.code() == 200 || response.code() == 201) {
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("payoutId", payoutId);
                result.put("apiVersion", apiVersion);
                result.put("environment", environment);
                result.put("response", objectMapper.readValue(responseBody, Object.class));
                result.put("statusCode", response.code());
                
                return result;
            } else {
                Map<String, Object> error = new HashMap<>();
                error.put("status", response.code());
                error.put("message", "Failed to initiate payout via " + apiVersion);
                
                // Try to parse error response as JSON, otherwise use raw string
                try {
                    error.put("responseData", objectMapper.readValue(responseBody, Object.class));
                } catch (Exception e) {
                    error.put("responseData", responseBody);
                }
                
                error.put("payoutId", payoutId);
                throw new RuntimeException(objectMapper.writeValueAsString(error));
            }
        }
    }

    public static void main(String[] args) {
        String apiVersion = "v2";
        String environment = "sandbox";
        String amount = "1000";
        String currency = "UGX";
        String msisdn = "256783456789";
        String message = "Test pay java";
        String reference = "PAYOUT123456";

        // Parse command line arguments
        for (int i = 0; i < args.length; i++) {
            if (("--version".equals(args[i]) || "-v".equals(args[i])) && i + 1 < args.length) {
                apiVersion = args[++i];
            } else if (("--environment".equals(args[i]) || "-e".equals(args[i])) && i + 1 < args.length) {
                environment = args[++i];
            } else if (("--amount".equals(args[i]) || "-a".equals(args[i])) && i + 1 < args.length) {
                amount = args[++i];
            } else if (("--currency".equals(args[i]) || "-c".equals(args[i])) && i + 1 < args.length) {
                currency = args[++i];
            } else if (("--msisdn".equals(args[i]) || "-m".equals(args[i])) && i + 1 < args.length) {
                msisdn = args[++i];
            } else if (("--message".equals(args[i]) || "-msg".equals(args[i])) && i + 1 < args.length) {
                message = args[++i];
            } else if (("--reference".equals(args[i]) || "-ref".equals(args[i])) && i + 1 < args.length) {
                reference = args[++i];
            }
        }

        System.out.println("🧪 Testing PawaPay Payout API " + apiVersion.toUpperCase() + "...");
        System.out.println("📊 Parameters:");
        System.out.println("   • Environment: " + environment);
        System.out.println("   • API Version: " + apiVersion);
        System.out.println("   • Amount: " + amount + " " + currency);
        System.out.println("   • MSISDN: " + msisdn);
        System.out.println("   • Message: " + message);
        System.out.println("   • Reference: " + reference);
        System.out.println("--------------------------------------------------");

        try {
            Map<String, Object> result = testPayout(environment, apiVersion, amount, currency, msisdn, message, reference);
            
            System.out.println("✅ SUCCESS!");
            System.out.println("📦 Payout ID: " + result.get("payoutId"));
            System.out.println("🔢 Status Code: " + result.get("statusCode"));
            System.out.println("🌍 Environment: " + result.get("environment"));
            System.out.println("📄 API Version: " + result.get("apiVersion"));
            System.out.println("\n📥 Full Response:\n" + objectMapper.writeValueAsString(result.get("response")));
            
        } catch (Exception e) {
            System.out.println("❌ ERROR!");
            System.out.println(e.getMessage());
        }

        // Force exit to kill OkHttp background threads instantly
        System.exit(0);
    }
}