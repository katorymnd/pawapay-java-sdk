// src/main/java/com/katorymnd/pawapay/examples/TestFetchConf.java
package com.katorymnd.pawapay.examples;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.katorymnd.pawapay.sdk.api.ApiClient;
import com.katorymnd.pawapay.sdk.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.stream.Stream;

public class TestFetchConf {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestFetchConf.class);
    // enable(SerializationFeature.INDENT_OUTPUT) ensures the JSON is pretty-printed in the file
    private static final ObjectMapper objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static final String DATA_DIR = "src/main/resources/data/";

    public static void main(String[] args) {
        System.out.println("\n==================================================");
        System.out.println("   PAWAPAY JAVA SDK - FETCH RAW CONFIG SCRIPT");
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
            // Ensure data directory exists
            Files.createDirectories(Paths.get(DATA_DIR));
        } catch (IOException e) {
            LOGGER.error("Failed to create data directory: {}", e.getMessage());
            return;
        }

        // --- RUN FOR BOTH VERSIONS ---
        String[] versions = {"v1", "v2"};
        for (String version : versions) {
            System.out.println("\n--------------------------------------------------");
            System.out.println(" 🚀 FETCHING RAW DATA FOR API VERSION: " + version.toUpperCase());
            System.out.println("--------------------------------------------------");
            runFetchForVersion(config, licenseKey, version);
        }

        System.out.println("\n✅ All raw configurations fetched successfully.\n");
    }

    private static void runFetchForVersion(Config config, String licenseKey, String version) {
        ApiClient client = null;
        try {
            // 3. INITIALIZE CLIENT
            LOGGER.info("Initializing ApiClient ({})...", version.toUpperCase());
            client = new ApiClient(config, licenseKey, true, version);

            // 4. FETCH DATA VIA API CLIENT
            LOGGER.info("Fetching MNO Availability for {}...", version.toUpperCase());
            Map<String, Object> mnoResponse = client.checkMnoAvailabilityAuto(null, null).join();
            
            LOGGER.info("Fetching Active Configuration for {}...", version.toUpperCase());
            Map<String, Object> confResponse = client.checkActiveConfAuto(null, null).join();

            // 5. SAVE RAW MNO AVAILABILITY
            int mnoStatus = (int) mnoResponse.get("status");
            if (mnoStatus >= 200 && mnoStatus < 300) {
                Object rawMnoData = mnoResponse.get("response");
                saveToFile(version, "mno_availability", rawMnoData);
                LOGGER.info("[{}] Successfully saved raw MNO Availability.", version.toUpperCase());
            } else {
                LOGGER.error("[{}] Failed to fetch MNO Availability: {}", version.toUpperCase(), mnoResponse);
            }

            // 6. SAVE RAW ACTIVE CONF
            int confStatus = (int) confResponse.get("status");
            if (confStatus >= 200 && confStatus < 300) {
                Object rawConfData = confResponse.get("response");
                saveToFile(version, "active_conf", rawConfData);
                LOGGER.info("[{}] Successfully saved raw Active Configuration.", version.toUpperCase());
            } else {
                LOGGER.error("[{}] Failed to fetch Active Configuration: {}", version.toUpperCase(), confResponse);
            }

        } catch (Exception e) {
            LOGGER.error("Test Execution Failed for {}: {}", version.toUpperCase(), e.getMessage(), e);
        } finally {
            if (client != null) {
                try {
                    client.close();
                    LOGGER.info("Client for {} closed.", version.toUpperCase());
                } catch (Exception e) {
                    LOGGER.error("Error closing client for {}: {}", version.toUpperCase(), e.getMessage());
                }
            }
        }
    }

    // --- FILE SAVING UTILITY ---
    private static void saveToFile(String apiVersion, String fileNameSuffix, Object data) {
        try {
            String fileName = apiVersion + "_" + fileNameSuffix + ".json";
            File file = new File(DATA_DIR + fileName);
            objectMapper.writeValue(file, data);
            System.out.println(" 💾 Saved: " + file.getAbsolutePath());
        } catch (IOException e) {
            LOGGER.error("Failed to save {}: {}", fileNameSuffix, e.getMessage());
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