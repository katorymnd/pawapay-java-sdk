// src/main/java/com/katorymnd/pawapay/examples/TestRefund.java
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

public class TestRefund {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestRefund.class);

    public static void main(String[] args) {
        System.out.println("\n==================================================");
        System.out.println("      PAWAPAY JAVA SDK - REFUND TEST SCRIPT");
        System.out.println("==================================================\n");

        // --- ARGUMENT PARSING LOGIC ---
        if (args.length == 0 || args[0].startsWith("-")) {
            LOGGER.error("MISSING DEPOSIT ID! Usage: pass <deposit_id> [-a <amount>] [-v <v1|v2>]");
            return;
        }

        String depositId = args[0];
        String amount = "1000";
        String testVersion = "v2";

        for (int i = 1; i < args.length; i++) {
            if (("-a".equals(args[i]) || "--amount".equals(args[i])) && i + 1 < args.length) {
                amount = args[++i];
            } else if (("-v".equals(args[i]) || "--version".equals(args[i])) && i + 1 < args.length) {
                testVersion = args[++i].toLowerCase();
            }
        }

        // 1. Load Environment variables locally from .env
        loadDotEnv();

        // 2. SETUP CONFIGURATION
        String apiToken = System.getProperty("PAWAPAY_SANDBOX_API_TOKEN");
        String licenseKey = System.getProperty("KATORYMND_PAWAPAY_SDK_LICENSE_KEY");

        if (apiToken == null || licenseKey == null) {
            LOGGER.error("MISSING CREDENTIALS! Please check your .env file or system properties.");
            return;
        }

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
            String refundId = Helpers.generateUniqueId();
            String currency = "UGX";

            LOGGER.info("Refund ID:   {}", refundId);
            LOGGER.info("Deposit ID:  {}", depositId);
            LOGGER.info("Amount:      {} {}", amount, currency);

            // Metadata (Optional)
            List<Object> metaData = new ArrayList<>();
            metaData.add(createMeta("reason", "Customer request"));
            metaData.add(createMeta("adminUser", "Admin-01"));

            // 5. EXECUTE REQUEST
            Map<String, Object> response;

            if ("v1".equalsIgnoreCase(testVersion)) {
                LOGGER.info("Initiating Refund (V1)...");
                // V1: Does not require currency (infers from deposit)
                response = client.initiateRefund(refundId, depositId, amount, metaData).join();
            } else {
                LOGGER.info("Initiating Refund (V2)...");
                // V2: Requires currency
                response = client.initiateRefundV2(refundId, depositId, amount, currency, metaData).join();
            }

            // 6. HANDLE RESPONSE
            int statusCode = (int) response.get("status");
            LOGGER.info("API Status: {}", statusCode);

            if (statusCode < 200 || statusCode > 202) {
                LOGGER.error("Refund Failed: {}", response);
                return;
            }

            System.out.println("\n--- Refund Initiation Response ---");
            System.out.println(response.get("response"));
            System.out.println("----------------------------------\n");

            // 7. CHECK STATUS
            LOGGER.info("Checking Transaction Status...");
            Thread.sleep(2000);

            // Note: We check the status of the REFUND_ID, not the Deposit ID
            Map<String, Object> statusResponse = client.checkTransactionStatusAuto(refundId, "refund").join();

            System.out.println("\n--- Refund Status Check ---");
            System.out.println(statusResponse.get("response"));
            System.out.println("---------------------------\n");

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