package com.katorymnd.pawapay.examples.direct;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Java SDK for PawaPay API - Direct refund testing
 */
public class TestRefund {

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
    private static final ObjectMapper objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    // UUID v4 Regex pattern for depositId validation
    private static final Pattern UUID_V4_PATTERN = 
        Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$", Pattern.CASE_INSENSITIVE);

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

    /**
     * Validates if a string is a valid UUID v4
     */
    public static boolean isValidUuidV4(String uuid) {
        return uuid != null && UUID_V4_PATTERN.matcher(uuid).matches();
    }

    public static Map<String, Object> testRefund(
            String environment,
            String apiVersion,
            String depositId,
            String amount,
            String currency) throws IOException {

        // 1. Validate environment
        if (!CONFIG.containsKey(environment)) {
            throw new IllegalArgumentException("Invalid environment: " + environment);
        }

        // 2. Validate Deposit ID (Strict UUID v4 check)
        if (!isValidUuidV4(depositId)) {
            throw new IllegalArgumentException("depositId is required and must be a valid UUID v4.");
        }

        String apiBaseUrl = CONFIG.get(environment).get("api_url");
        String apiToken = CONFIG.get(environment).get("api_token");

        // 3. Build endpoint
        String endpoint = "v2".equals(apiVersion) ? "/v2/refunds" : "/refunds";
        String apiUrl = apiBaseUrl + endpoint;

        // 4. Generate Refund ID
        String refundId = generateUuidV4();

        // 5. Build payload
        ObjectNode payload = objectMapper.createObjectNode();

        if ("v2".equals(apiVersion)) {
            // V2 Payload
            payload.put("refundId", refundId);
            payload.put("depositId", depositId);
            payload.put("amount", amount);
            payload.put("currency", currency);
            
            // V2 Metadata format
            ArrayNode metadata = payload.putArray("metadata");
            
            ObjectNode orderItem = metadata.addObject();
            orderItem.put("orderId", "ORD-123456789");
            
            ObjectNode customerItem = metadata.addObject();
            customerItem.put("customerId", "customer@email.com");
            customerItem.put("isPII", true);
            
        } else {
            // V1 Payload
            payload.put("refundId", refundId);
            payload.put("depositId", depositId);
            payload.put("amount", amount);
            
            // V1 Metadata format
            ArrayNode metadata = payload.putArray("metadata");
            
            ObjectNode orderItem = metadata.addObject();
            orderItem.put("fieldName", "orderId");
            orderItem.put("fieldValue", "ORD-123456789");
            
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
            Object responseJson;
            
            // Try to parse response as JSON
            try {
                responseJson = objectMapper.readValue(responseBody, Object.class);
            } catch (Exception e) {
                responseJson = responseBody;
            }

            // Check for success (200 OK)
            if (response.code() == 200) {
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("httpStatus", response.code());
                result.put("apiVersion", apiVersion);
                result.put("refundId", refundId);
                result.put("depositId", depositId);
                result.put("amount", amount);
                if ("v2".equals(apiVersion)) {
                    result.put("currency", currency);
                }
                result.put("data", responseJson);
                result.put("rawResponse", responseJson);
                
                return result;
            } else {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "non_success_status");
                error.put("httpStatus", response.code());
                error.put("apiVersion", apiVersion);
                error.put("refundId", refundId);
                error.put("depositId", depositId);
                error.put("rawResponse", responseJson);
                
                return error;
            }

        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "http_error");
            error.put("status", 500);
            error.put("message", e.getMessage());
            error.put("responseData", e.getMessage());
            
            throw new RuntimeException(objectMapper.writeValueAsString(error));
        }
    }

    public static void main(String[] args) {
        String apiVersion = "v1";
        String environment = "sandbox";
        String depositId = null;
        String amount = "1000";
        String currency = "UGX";

        // Parse command line arguments
        for (int i = 0; i < args.length; i++) {
            if (("--version".equals(args[i]) || "-v".equals(args[i])) && i + 1 < args.length) {
                apiVersion = args[++i];
            } else if (("--environment".equals(args[i]) || "-e".equals(args[i])) && i + 1 < args.length) {
                environment = args[++i];
            } else if (("--depositId".equals(args[i]) || "-d".equals(args[i])) && i + 1 < args.length) {
                depositId = args[++i];
            } else if (("--amount".equals(args[i]) || "-a".equals(args[i])) && i + 1 < args.length) {
                amount = args[++i];
            } else if (("--currency".equals(args[i]) || "-c".equals(args[i])) && i + 1 < args.length) {
                currency = args[++i];
            }
        }

        // Validate required depositId
        if (depositId == null) {
            System.out.println("❌ ERROR: depositId is required!");
            System.out.println("Usage: mvn exec:java \"-Dexec.mainClass=com.katorymnd.pawapay.examples.direct.TestRefund\" \"-Dexec.args=-d <depositId> [-v v1|v2] [-a amount] [-c currency] [-e sandbox|production]\"");
            System.exit(1);
        }

        System.out.println("💰 Testing PawaPay Refund API " + apiVersion.toUpperCase() + "...");
        System.out.println("📊 Parameters:");
        System.out.println("   • Environment: " + environment);
        System.out.println("   • API Version: " + apiVersion);
        System.out.println("   • Deposit ID: " + depositId);
        System.out.println("   • Amount: " + amount + (apiVersion.equals("v2") ? " " + currency : ""));
        if (apiVersion.equals("v2")) {
            System.out.println("   • Currency: " + currency);
        }
        System.out.println("--------------------------------------------------");

        try {
            Map<String, Object> result = testRefund(environment, apiVersion, depositId, amount, currency);
            
            if (result.containsKey("success") && Boolean.TRUE.equals(result.get("success"))) {
                System.out.println("✅ SUCCESS!");
                System.out.println("💰 Refund ID: " + result.get("refundId"));
                System.out.println("📦 Deposit ID: " + result.get("depositId"));
                System.out.println("🔢 HTTP Status: " + result.get("httpStatus"));
                System.out.println("📄 API Version: " + result.get("apiVersion"));
                System.out.println("💵 Amount: " + result.get("amount") + 
                    (result.containsKey("currency") ? " " + result.get("currency") : ""));
                System.out.println("\n📥 Full Response:\n" + objectMapper.writeValueAsString(result.get("data")));
            } else {
                System.out.println("❌ FAILED!");
                System.out.println("🔢 HTTP Status: " + result.get("httpStatus"));
                System.out.println("📝 Error: " + result.get("error"));
                System.out.println("\n📥 Error Response:\n" + objectMapper.writeValueAsString(result.get("rawResponse")));
            }
            
        } catch (Exception e) {
            System.out.println("❌ ERROR!");
            System.out.println(e.getMessage());
        }

        // Force exit to kill OkHttp background threads instantly
        System.exit(0);
    }
}