// src/main/java/com/katorymnd/pawapay/examples/TestDeposit.java
package com.katorymnd.pawapay.examples;

import com.katorymnd.pawapay.sdk.api.ApiClient;
import com.katorymnd.pawapay.sdk.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import com.katorymnd.pawapay.sdk.utils.Helpers;
import java.util.stream.Stream;

public class TestDeposit {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestDeposit.class);

    public static void main(String[] args) {
        System.out.println("\n==================================================");
        System.out.println("   PAWAPAY JAVA SDK - DEPOSIT TEST SCRIPT");
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

        Config config = new Config.Builder()
                .apiKey(apiToken)
                .environment("sandbox")
                .timeout(30000)
                .build();

        try {
            // Set the API version manually here
            String apiVersion = "v2"; 

            // 3. INITIALIZE CLIENT
            LOGGER.info("Initializing ApiClient with version {}...", apiVersion);
            ApiClient client = new ApiClient(config, licenseKey, true, apiVersion);

            String depositId = Helpers.generateUniqueId();
            LOGGER.info("Generated Deposit ID: {}", depositId);

            // 4. INITIATE DEPOSIT
            String amount = "5000";
            String currency = "UGX";
            String payerMsisdn = "256783456789";
            String provider = "MTN_MOMO_UGA";

            // Dynamically log the current version being used
            LOGGER.info("Initiating Deposit ({})...", apiVersion.toUpperCase());
            
            // Note: .join() waits for the CompletableFuture to complete, acting like Python's 'await'
            Map<String, Object> response = client.initiateDepositV2(
                    depositId, amount, currency, payerMsisdn, provider, 
                    "Java SDK Test", null, null, null
            ).join();

            // 5. HANDLE RESPONSE
            int statusCode = (int) response.get("status");
            LOGGER.info("Initiation API Status: {}", statusCode);

            if (statusCode < 200 || statusCode > 202) {
                LOGGER.error("Deposit Initiation Failed: {}", response);
                return;
            }

            System.out.println("\n--- Initiation Response Data ---");
            System.out.println(response.get("response"));
            System.out.println("--------------------------------\n");

            // 6. CHECK STATUS
            LOGGER.info("Checking Transaction Status...");

            // Small delay to allow Sandbox to process
            Thread.sleep(2000);

            Map<String, Object> statusResponse = client.checkTransactionStatusAuto(depositId, "deposit").join();

            System.out.println("\n--- Status Check Response ---");
            System.out.println(statusResponse.get("response"));
            System.out.println("-----------------------------\n");

            client.close();

        } catch (Exception e) {
            LOGGER.error("Test Execution Failed: {}", e.getMessage(), e);
        }
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