// src/main/java/com/katorymnd/pawapay/examples/TestPayout.java
package com.katorymnd.pawapay.examples;

import com.katorymnd.pawapay.sdk.api.ApiClient;
import com.katorymnd.pawapay.sdk.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.katorymnd.pawapay.sdk.utils.Helpers;
import java.util.stream.Stream;

public class TestPayout {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestPayout.class);

    public static void main(String[] args) {
        System.out.println("\n==================================================");
        System.out.println("      PAWAPAY JAVA SDK - PAYOUT TEST SCRIPT");
        System.out.println("==================================================\n");

        // 1. Load Environment variables locally from .env
        loadDotEnv();

        // 2. SETUP CONFIGURATION
        String apiToken = System.getProperty("PAWAPAY_SANDBOX_API_TOKEN");
        String licenseKey = System.getProperty("KATORYMND_PAWAPAY_SDK_LICENSE_KEY");

        if (apiToken == null || licenseKey == null) {
            LOGGER.error("MISSING CREDENTIALS! Please check your .env file or system properties.");
            return;
        }

        // TOGGLE VERSION HERE: "v1" or "v2"
        String testVersion = "v2";

        Config config = new Config.Builder()
                .apiKey(apiToken)
                .environment("sandbox")
                .timeout(30000)
                .build();

        ApiClient client = null;

        try {
            // 3. INITIALIZE CLIENT
            LOGGER.info("Initializing ApiClient ({})...", testVersion.toUpperCase());
            client = new ApiClient(config, licenseKey, true, testVersion);

            // 4. PREPARE DATA
            String payoutId = Helpers.generateUniqueId();
            String amount = "5000";
            String currency = "UGX";
            String msisdn = "256783456789"; // Valid Sandbox MSISDN
            String description = "Java SDK Payout Test";

            // --- OPTIONAL METADATA ---
            // This helps you track the payment in your own system
            List<Object> metaData = new ArrayList<>();
            metaData.add(createMeta("orderId", "ORD-998877"));
            metaData.add(createMeta("customerId", "CUST-001"));
            metaData.add(createMeta("reason", "Refund for return"));

            LOGGER.info("Generated Payout ID: {}", payoutId);

            // 5. EXECUTE REQUEST
            Map<String, Object> response;

            if ("v1".equalsIgnoreCase(testVersion)) {
                LOGGER.info("Initiating Payout (V1)...");
                
                // V1 uses 'correspondent'
                String correspondent = "MTN_MOMO_UGA";
                
                response = client.initiatePayout(
                        payoutId, amount, currency, correspondent, msisdn, description, metaData
                ).join();
            } else {
                LOGGER.info("Initiating Payout (V2)...");
                
                // V2 uses 'provider' (Must be MTN_MOMO_UGA in Sandbox)
                String provider = "MTN_MOMO_UGA";
                
                response = client.initiatePayoutV2(
                        payoutId, amount, currency, msisdn, provider, description, metaData
                ).join();
            }

            // 6. HANDLE RESPONSE
            int statusCode = (int) response.get("status");
            LOGGER.info("API Status: {}", statusCode);

            if (statusCode < 200 || statusCode > 202) {
                LOGGER.error("Payout Initiation Failed: {}", response);
                return;
            }

            System.out.println("\n--- Payout Initiation Response ---");
            System.out.println(response.get("response"));
            System.out.println("----------------------------------\n");

            // 7. CHECK STATUS
            LOGGER.info("Checking Transaction Status...");
            Thread.sleep(2000); // Wait for Sandbox

            Map<String, Object> statusResponse = client.checkTransactionStatusAuto(payoutId, "payout").join();

            System.out.println("\n--- Status Check Response ---");
            System.out.println(statusResponse.get("response"));
            System.out.println("-----------------------------\n");

        } catch (Exception e) {
            LOGGER.error("Test Execution Failed: {}", e.getMessage(), e);
        } finally {
            if (client != null) {
                try {
                    client.close();
                    LOGGER.info("Client closed.");
                } catch (Exception e) {
                    LOGGER.error("Error closing client: {}", e.getMessage());
                }
            }
        }
    }

    /**
     * Helper method to format Metadata cleanly
     */
    private static Map<String, Object> createMeta(String fieldName, String fieldValue) {
        Map<String, Object> meta = new HashMap<>();
        meta.put("fieldName", fieldName);
        meta.put("fieldValue", fieldValue);
        return meta;
    }

    /**
     * Minimal utility to load a .env file into System Properties for local testing.
     */
    private static void loadDotEnv() {
        try (Stream<String> stream = Files.lines(Paths.get(System.getProperty("user.dir"), ".env"))) {
            stream.filter(line -> line.contains("=") && !line.trim().startsWith("#"))
                  .forEach(line -> {
                      String[] parts = line.split("=", 2);
                      if (parts.length == 2) {
                          System.setProperty(parts[0].trim(), parts[1].trim());
                      }
                  });
        } catch (IOException e) {
            LOGGER.warn("Could not load .env file. Relying on existing System/Environment properties.");
        }
    }
}