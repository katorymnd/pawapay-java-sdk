package com.katorymnd.pawapay.examples.direct;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import okhttp3.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Java SDK for fetching PawaPay Configuration or Availability
 */
public class FetchPawapayConf {

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
    
    // Define the path to the Maven resources data directory
    private static final String DATA_DIR = "src/main/resources/data/";

    static {
        // SSL Context - Disable SSL verification for development
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS);

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
        
        // Ensure data directory exists
        try {
            Files.createDirectories(Paths.get(DATA_DIR));
        } catch (IOException e) {
            System.err.println("Could not create data directory: " + e.getMessage());
        }
    }

    public static Map<String, Object> fetchConfig(
            String environment,
            String apiVersion,
            String endpointType,
            String country,
            String operationType) throws IOException {

        if (!CONFIG.containsKey(environment)) {
            throw new IllegalArgumentException("Invalid environment: " + environment);
        }

        String apiBaseUrl = CONFIG.get(environment).get("api_url");
        String apiToken = CONFIG.get(environment).get("api_token");

        // Resolve Endpoint Path
        String path;
        if ("availability".equals(endpointType)) {
            path = "v2".equals(apiVersion) ? "/v2/availability" : "/availability";
        } else {
            path = "v2".equals(apiVersion) ? "/v2/active-conf" : "/active-conf";
        }

        HttpUrl.Builder urlBuilder = HttpUrl.parse(apiBaseUrl + path).newBuilder();

        // Prepare Query Params
        if ("availability".equals(endpointType)) {
            if (country != null && !country.isEmpty()) {
                urlBuilder.addQueryParameter("country", country);
            }
            if (operationType != null && !operationType.isEmpty()) {
                urlBuilder.addQueryParameter("operationType", operationType);
            }
        }

        String apiUrl = urlBuilder.build().toString();

        Request request = new Request.Builder()
                .url(apiUrl)
                .get()
                .addHeader("Authorization", "Bearer " + apiToken)
                .addHeader("Content-Type", "application/json")
                .build();

        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            
            if (!response.isSuccessful()) {
                Map<String, Object> error = new HashMap<>();
                error.put("status", response.code());
                error.put("message", "Failed to fetch " + endpointType);
                error.put("responseData", responseBody);
                throw new RuntimeException(objectMapper.writeValueAsString(error));
            }

            Object jsonData = objectMapper.readValue(responseBody, Object.class);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("endpointType", endpointType);
            result.put("apiVersion", apiVersion);
            result.put("environment", environment);
            result.put("data", jsonData);

            // Save to file
            saveToFile(apiVersion, endpointType, result);

            return result;
        }
    }

    private static void saveToFile(String apiVersion, String endpointType, Map<String, Object> data) {
        try {
            // e.g., v1_active_conf.json or v2_availability.json
            String fileName = apiVersion + "_" + endpointType.replace("-", "_") + ".json";
            File file = new File(DATA_DIR + fileName);
            objectMapper.writeValue(file, data);
            System.out.println("💾 Saved configuration to: " + file.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("Failed to save config file: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        // Arrays defining the combinations we want to fetch
        String[] versions = {"v1", "v2"};
        String[] endpoints = {"active-conf", "availability"};

        System.out.println("🚀 Starting batch fetch for all PawaPay configurations...\n");

        for (String version : versions) {
            for (String endpoint : endpoints) {
                try {
                    System.out.println("Fetching: " + version + " -> " + endpoint + "...");
                    // Call the API and save the file
                    fetchConfig("sandbox", version, endpoint, null, null);
                } catch (Exception e) {
                    System.out.println("❌ ERROR fetching " + version + " " + endpoint + ": " + e.getMessage());
                }
            }
        }
        
        System.out.println("\n✅ Batch update complete.");
        
        // Force exit to close OkHttp background threads instantly
        System.exit(0);
    }
}