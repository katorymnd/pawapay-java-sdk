package com.katorymnd.pawapay.examples.direct;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import okhttp3.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Java SDK for PawaPay API - Direct deposit status check
 */
public class TransactionIdCheckTest {

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

    /**
     * Validates if a string is a valid UUID v4
     */
    public static boolean isValidUuidV4(String uuid) {
        return uuid != null && UUID_V4_PATTERN.matcher(uuid).matches();
    }

    /**
     * Safely converts an Object to a Map<String, Object> if possible
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> toMap(Object obj) {
        if (obj instanceof Map) {
            return (Map<String, Object>) obj;
        }
        return new HashMap<>();
    }

    public static Map<String, Object> checkDepositStatus(
            String environment,
            String apiVersion,
            String depositId) throws IOException {

        // 1. Validate environment
        if (!CONFIG.containsKey(environment)) {
            throw new IllegalArgumentException("Invalid environment: " + environment);
        }

        // 2. Validate Deposit ID (Strict UUID v4 check)
        if (!isValidUuidV4(depositId)) {
            throw new IllegalArgumentException("depositId must be a valid UUID v4.");
        }

        String apiBaseUrl = CONFIG.get(environment).get("api_url");
        String apiToken = CONFIG.get(environment).get("api_token");

        // 3. Build endpoint per version
        String endpoint;
        if ("v2".equals(apiVersion)) {
            endpoint = "/v2/deposits/" + depositId;
        } else {
            endpoint = "/deposits/" + depositId;
        }
        String apiUrl = apiBaseUrl + endpoint;

        // Build request
        Request request = new Request.Builder()
                .url(apiUrl)
                .get()
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
                result.put("depositId", depositId);
                
                if ("v2".equals(apiVersion)) {
                    // V2: { "status": "FOUND" | "NOT_FOUND", "data": {...} }
                    Map<String, Object> v2Response = toMap(responseJson);
                    String status = v2Response.get("status") != null ? v2Response.get("status").toString() : "";
                    boolean found = "FOUND".equals(status);
                    
                    result.put("found", found);
                    result.put("data", v2Response.get("data"));
                } else {
                    // V1 logic: returns array or object. "found" if truthy.
                    boolean found = false;
                    
                    if (responseJson instanceof Iterable) {
                        // It's a list/array
                        Iterable<?> list = (Iterable<?>) responseJson;
                        java.util.Iterator<?> iterator = list.iterator();
                        found = iterator.hasNext();
                    } else if (responseJson instanceof Map) {
                        // It's a map/object
                        Map<?, ?> map = (Map<?, ?>) responseJson;
                        found = !map.isEmpty();
                    } else {
                        // It's a primitive or other type
                        found = responseJson != null && 
                                !responseJson.toString().isEmpty() && 
                                !"null".equals(responseJson.toString());
                    }
                    
                    result.put("found", found);
                    result.put("data", responseJson);
                }
                
                result.put("rawResponse", responseJson);
                
                return result;
            } else {
                // Handle non-200 responses gracefully
                Map<String, Object> error = new HashMap<>();
                error.put("error", "non_success_status");
                error.put("httpStatus", response.code());
                error.put("apiVersion", apiVersion);
                error.put("rawResponse", responseJson);
                
                return error;
            }

        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "http_error");
            error.put("httpStatus", 500);
            error.put("message", e.getMessage());
            error.put("rawResponse", e.getMessage());
            
            return error;
        }
    }

    public static void main(String[] args) {
        String apiVersion = "v2";
        String environment = "sandbox";
        String depositId = null;

        // Parse command line arguments
        for (int i = 0; i < args.length; i++) {
            if (("--version".equals(args[i]) || "-v".equals(args[i])) && i + 1 < args.length) {
                apiVersion = args[++i];
            } else if (("--environment".equals(args[i]) || "-e".equals(args[i])) && i + 1 < args.length) {
                environment = args[++i];
            } else if (("--depositId".equals(args[i]) || "-d".equals(args[i])) && i + 1 < args.length) {
                depositId = args[++i];
            }
        }

        // Validate required depositId
        if (depositId == null) {
            System.out.println("❌ ERROR: depositId is required!");
            System.out.println("Usage: mvn exec:java \"-Dexec.mainClass=com.katorymnd.pawapay.examples.direct.TransactionIdCheckTest\" \"-Dexec.args=-d <depositId> [-v v1|v2] [-e sandbox|production]\"");
            System.exit(1);
        }

        System.out.println("🔍 Testing PawaPay Deposit Status Check API " + apiVersion.toUpperCase() + "...");
        System.out.println("📊 Parameters:");
        System.out.println("   • Environment: " + environment);
        System.out.println("   • API Version: " + apiVersion);
        System.out.println("   • Deposit ID: " + depositId);
        System.out.println("--------------------------------------------------");

        try {
            Map<String, Object> result = checkDepositStatus(environment, apiVersion, depositId);
            
            if (result.containsKey("success") && Boolean.TRUE.equals(result.get("success"))) {
                System.out.println("✅ SUCCESS!");
                System.out.println("💰 Deposit ID: " + result.get("depositId"));
                System.out.println("🔢 HTTP Status: " + result.get("httpStatus"));
                System.out.println("📄 API Version: " + result.get("apiVersion"));
                System.out.println("🔍 Found: " + result.get("found"));
                
                if (Boolean.TRUE.equals(result.get("found"))) {
                    System.out.println("\n📥 Deposit Data:\n" + objectMapper.writeValueAsString(result.get("data")));
                } else {
                    System.out.println("\n📝 Message: Deposit not found");
                }
                
                System.out.println("\n📥 Full Response:\n" + objectMapper.writeValueAsString(result.get("rawResponse")));
            } else {
                System.out.println("❌ FAILED!");
                System.out.println("🔢 HTTP Status: " + result.get("httpStatus"));
                System.out.println("📝 Error: " + result.get("error"));
                if (result.containsKey("message")) {
                    System.out.println("📝 Message: " + result.get("message"));
                }
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