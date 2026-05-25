// src/main/java/com/katorymnd/pawapay/sdk/utils/vm/SetupSDK.java
package com.katorymnd.pawapay.sdk.utils.vm;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

public class SetupSDK {

    private static final String PROJECT_ROOT = System.getProperty("user.dir");
    // Generate into resources so it gets packed into the JAR
    private static final String VM_OUTPUT_DIR = PROJECT_ROOT + "/src/main/resources/com/katorymnd/pawapay/sdk/utils/vm";
    private static final ObjectMapper mapper = new ObjectMapper();

    public static void main(String[] args) {
        System.out.println("\n============================================================");
        System.out.println("  PAWAPAY JAVA SDK - FULL SETUP");
        System.out.println("============================================================");

        // 0. Load the .env file so the setup knows the Domain and Secret!
        loadDotEnv();

        // 1. Ensure required environment variables are set
        String domain = System.getProperty("PAWAPAY_SDK_LICENSE_DOMAIN", System.getenv("PAWAPAY_SDK_LICENSE_DOMAIN"));
        String secret = System.getProperty("PAWAPAY_SDK_LICENSE_SECRET", System.getenv("PAWAPAY_SDK_LICENSE_SECRET"));
        
        if (domain == null || domain.trim().isEmpty()) {
            System.err.println("[Setup] ERROR: PAWAPAY_SDK_LICENSE_DOMAIN is not set. Please check your .env file.");
            System.exit(1);
        }
        
        if (secret == null || secret.trim().isEmpty()) {
            System.err.println("[Setup] ERROR: PAWAPAY_SDK_LICENSE_SECRET is not set. Please check your .env file.");
            System.exit(1);
        }
        
        System.out.println("[Setup] License domain: " + domain);
        System.out.println("[Setup] License secret: " + secret.substring(0, Math.min(4, secret.length())) + "...");

        // 2. Ensure VM Directory
        File outputDir = new File(VM_OUTPUT_DIR);
        if (!outputDir.exists()) {
            outputDir.mkdirs();
            System.out.println("[Setup] Created directory: " + VM_OUTPUT_DIR);
        }

        // 3. Initialize Encoder
        System.out.println("[Setup] Initializing Bytecode Encoder...");
        BytecodeEncoder encoder = new BytecodeEncoder();

        // 4. Generate VM Files
        System.out.println("[Setup] Generating VM Bytecode...");
        boolean success = encoder.generateClientFiles(VM_OUTPUT_DIR);

        if (success) {
            // 5. Generate Session File
            System.out.println("[Setup] Generating Session Cache...");
            generateSessionFile();

            System.out.println("\n============================================================");
            System.out.println("SUCCESS! SDK is fully bootstrapped.");
            System.out.println("============================================================");
            System.out.println("1. Imprint:  " + Paths.get(PROJECT_ROOT, ".pawapay-imprint"));
            System.out.println("2. Session:  " + Paths.get(PROJECT_ROOT, ".pawapay-session"));
            System.out.println("3. Opcodes:  " + Paths.get(VM_OUTPUT_DIR, "opcodes.json"));
            System.out.println("4. Bytecode: " + Paths.get(VM_OUTPUT_DIR, "bytecode.bin"));
            System.out.println("\n[Next Step] Run: com.katorymnd.pawapay.examples.TestDeposit");
        } else {
            System.err.println("\n[Setup] FAILED to generate VM files.");
            System.exit(1);
        }
    }

    private static void generateSessionFile() {
        Path imprintPath = Paths.get(PROJECT_ROOT, ".pawapay-imprint");
        Path sessionPath = Paths.get(PROJECT_ROOT, ".pawapay-session");

        if (!Files.exists(imprintPath)) {
            System.out.println("[Setup] Cannot generate session: .pawapay-imprint missing.");
            return;
        }

        try {
            // 1. Read Imprint
            String imprint = new String(Files.readAllBytes(imprintPath), StandardCharsets.UTF_8).trim();

            // 2. Create Timestamp & Signature
            long timestamp = System.currentTimeMillis() / 1000;
            String rawSig = timestamp + imprint;
            String signature = hashSha256(rawSig);

            // 3. Prepare JSON Data
            Map<String, Object> data = new HashMap<>();
            data.put("lastValidation", timestamp);
            data.put("signature", signature);

            // 4. Base64 Encode
            String jsonStr = mapper.writeValueAsString(data);
            String content = Base64.getEncoder().encodeToString(jsonStr.getBytes(StandardCharsets.UTF_8));

            // 5. Write File
            Files.write(sessionPath, content.getBytes(StandardCharsets.UTF_8));

            System.out.println("[Setup] Generated Session File: " + sessionPath);
            System.out.println("        (SDK believes it was validated at " + timestamp + ")");

        } catch (Exception e) {
            System.err.println("[Setup] Failed to generate session file: " + e.getMessage());
        }
    }

    private static String hashSha256(String input) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Minimal utility to load a .env file into System Properties for local setup.
     */
    private static void loadDotEnv() {
        Path envPath = Paths.get(PROJECT_ROOT, ".env");
        if (!Files.exists(envPath)) {
            System.err.println("[Setup] ERROR: No .env file found at " + envPath);
            System.err.println("[Setup] Please create a .env file with PAWAPAY_SDK_LICENSE_DOMAIN and PAWAPAY_SDK_LICENSE_SECRET");
            System.exit(1);
        }
        
        try (Stream<String> stream = Files.lines(envPath)) {
            stream.filter(line -> line.contains("=") && !line.trim().startsWith("#"))
                  .forEach(line -> {
                      String[] parts = line.split("=", 2);
                      if (parts.length == 2) {
                          System.setProperty(parts[0].trim(), parts[1].trim());
                      }
                  });
            System.out.println("[Setup] Successfully loaded variables from .env");
        } catch (IOException e) {
            System.err.println("[Setup] Could not load .env file: " + e.getMessage());
            System.exit(1);
        }
    }
}