// src/main/java/com/katorymnd/pawapay/examples/TestPaymentPage.java
package com.katorymnd.pawapay.examples;

import com.katorymnd.pawapay.sdk.api.ApiClient;
import com.katorymnd.pawapay.sdk.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import com.katorymnd.pawapay.sdk.utils.Helpers;
import java.util.stream.Stream;

public class TestPaymentPage {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestPaymentPage.class);

    public static void main(String[] args) {
        System.out.println("\n==================================================");
        System.out.println("   PAWAPAY JAVA SDK - PAYMENT PAGE TEST SCRIPT");
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
        String testVersion = "v1";

        Config config = new Config.Builder()
                .apiKey(apiToken)
                .environment("sandbox")
                .timeout(30000)
                .build();

        ApiClient client = null;

        try {
            // 3. INITIALIZE CLIENT
            LOGGER.info("Initializing ApiClient ({})...", testVersion.toUpperCase());
            // Passing true for SSL verification and specifying the version
            client = new ApiClient(config, licenseKey, true, testVersion);

            // 4. PREPARE DATA
            String depositId = Helpers.generateUniqueId();
            String returnUrl = "https://example.com/payment-success";
            String amount = "5000";
            String currency = "UGX";
            String msisdn = "256783456789";
            
            // Keep description short for V1 compatibility (max 22 chars)
            String description = "Java SDK Test";

            LOGGER.info("Generated Deposit ID: {}", depositId);

            // 5. EXECUTE REQUEST
            Map<String, Object> response;
            Map<String, Object> params = new HashMap<>();

            if ("v1".equalsIgnoreCase(testVersion)) {
                LOGGER.info("Creating Payment Page Session (V1)...");
                
                // V1 Params
                params.put("depositId", depositId);
                params.put("amount", amount);
                params.put("returnUrl", returnUrl);
                params.put("statementDescription", description);
                params.put("reason", "Payment");
                params.put("msisdn", msisdn);
                params.put("country", "UGA");
                
                response = client.createPaymentPageSessionAuto(params).join();
            } else {
                LOGGER.info("Creating Payment Page Session (V2)...");
                
                // V2 Params
                params.put("depositId", depositId);
                params.put("amount", amount);
                params.put("currency", currency);
                params.put("returnUrl", returnUrl);
                params.put("customerMessage", description);
                params.put("country", "UGA");
                params.put("phoneNumber", msisdn); // Pre-fills phone on V2 page
                
                response = client.createPaymentPageSessionAuto(params).join();
            }

            // 6. RESULT
            int statusCode = (int) response.get("status");
            LOGGER.info("API Status: {}", statusCode);

            if (statusCode < 200 || statusCode > 202) {
                LOGGER.error("Creation Failed: {}", response);
                return;
            }

            // Safely extract the redirect URL from the nested response map
            String redirectUrl = "Unknown";
            Object responseDataObj = response.get("response");
            if (responseDataObj instanceof Map) {
                Map<?, ?> responseData = (Map<?, ?>) responseDataObj;
                Object urlObj = responseData.get("redirectUrl");
                if (urlObj == null) {
                    urlObj = responseData.get("url"); // Fallback check
                }
                if (urlObj != null) {
                    redirectUrl = urlObj.toString();
                }
            }

            System.out.println("\n----------------------------------------");
            System.out.println(" SUCCESS! Payment Page Created (" + testVersion.toUpperCase() + ")");
            System.out.println("----------------------------------------");
            System.out.println(" Deposit ID:   " + depositId);
            // Added space and clear instructions for terminal clicking
            System.out.println(" Redirect URL: " + redirectUrl);
            System.out.println("               ^ (Ctrl+Click or Cmd+Click to open)");
            System.out.println(" Raw Data:     " + responseDataObj);
            System.out.println("----------------------------------------\n");

            // 7. AUTO-OPEN IN BROWSER
            if (!"Unknown".equals(redirectUrl) && Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                try {
                    LOGGER.info("Opening Payment Page in your default browser...");
                    Desktop.getDesktop().browse(new URI(redirectUrl));
                } catch (Exception ex) {
                    LOGGER.warn("Could not automatically open the browser. Please click the link above.");
                }
            }

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