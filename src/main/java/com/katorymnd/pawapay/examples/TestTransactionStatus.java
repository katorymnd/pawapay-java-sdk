// src/main/java/com/katorymnd/pawapay/examples/TestTransactionStatus.java
package com.katorymnd.pawapay.examples;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.katorymnd.pawapay.sdk.api.ApiClient;
import com.katorymnd.pawapay.sdk.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.stream.Stream;

public class TestTransactionStatus {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestTransactionStatus.class);

    public static void main(String[] args) {
        System.out.println("\n==================================================");
        System.out.println("   PAWAPAY JAVA SDK - STATUS CHECK SCRIPT");
        System.out.println("==================================================\n");

        // --- ARGUMENT PARSING LOGIC ---
        if (args.length == 0 || args[0].startsWith("-")) {
            LOGGER.error("MISSING TRANSACTION ID! Usage: pass <transaction_id> [-v <v1|v2>] [-t <deposit|payout|refund>]");
            return;
        }

        String transactionId = args[0];
        String testVersion = "v2";        
        String transactionType = "deposit"; 

        for (int i = 1; i < args.length; i++) {
            if (("-v".equals(args[i]) || "--version".equals(args[i])) && i + 1 < args.length) {
                testVersion = args[++i].toLowerCase();
            } else if (("-t".equals(args[i]) || "--type".equals(args[i])) && i + 1 < args.length) {
                transactionType = args[++i].toLowerCase();
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

            // 4. CHECK STATUS
            LOGGER.info("Checking Status for ID: {}", transactionId);
            LOGGER.info("Type: {}", transactionType);
            
            // We use the auto method which switches based on the version provided
            Map<String, Object> response = client.checkTransactionStatusAuto(transactionId, transactionType).join();

            // 5. RESULT
            int statusCode = (int) response.get("status");
            LOGGER.info("API Status: {}", statusCode);

            if (statusCode == 404) {
                System.out.println("\n[!] Transaction Not Found (404)");
            } else if (statusCode != 200) {
                System.out.println("\n[!] Error: " + response);
            } else {
                System.out.println("\n--- Transaction Details ---");
                // Pretty print the JSON response just like Python
                ObjectMapper mapper = new ObjectMapper();
                String prettyJson = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(response.get("response"));
                System.out.println(prettyJson);
                System.out.println("---------------------------\n");
            }

        } catch (Exception e) {
            LOGGER.error("Check Failed: {}", e.getMessage(), e);
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