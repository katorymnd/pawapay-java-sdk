// src/main/java/com/katorymnd/pawapay/sdk/utils/license/IntegrityChecker.java
package com.katorymnd.pawapay.sdk.utils.license;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.security.MessageDigest;
import java.util.*;

/**
 * Code Integrity Checker - Detects if SDK class files have been tampered with
 */
public class IntegrityChecker {

    private static final Logger LOGGER = LoggerFactory.getLogger(IntegrityChecker.class);

    // Singleton instance
    private static final IntegrityChecker INSTANCE = new IntegrityChecker();

    private final Map<String, String> checksums = new HashMap<>();
    private boolean tampered = false;
    private final List<String> criticalFiles;

    private IntegrityChecker() {
        // In Java, we check the compiled bytecode (.class) files inside the JAR
        // instead of the raw .java source files.
        this.criticalFiles = Arrays.asList(
                "com/katorymnd/pawapay/sdk/api/ApiClient.class",
                "com/katorymnd/pawapay/sdk/utils/license/LicenseValidator.class",
                "com/katorymnd/pawapay/sdk/utils/license/ServerCheck.class",
                "com/katorymnd/pawapay/sdk/utils/license/ProtectionManager.class"
        );

        // Record checksums on first load
        recordChecksums();
    }

    public static IntegrityChecker getInstance() {
        return INSTANCE;
    }

    private void recordChecksums() {
        for (String relPath : criticalFiles) {
            try (InputStream is = getClass().getClassLoader().getResourceAsStream(relPath)) {
                if (is != null) {
                    byte[] content = is.readAllBytes();
                    String fileHash = hashSha256(content);
                    checksums.put(relPath, fileHash);
                } else {
                    // If file doesn't exist when recording, still set null to know it's missing
                    checksums.put(relPath, null);
                }
            } catch (Exception err) {
                // File might not exist in some bundled versions; record null and continue
                checksums.put(relPath, null);
            }
        }
    }

    public synchronized boolean verifyFile(String relPath) {
        // If we've already detected tampering, remain strict and return false
        if (tampered) {
            LOGGER.warn("[PawaPay Integrity] Previously flagged tamper state, refusing to re-validate: {}", relPath);
            return false;
        }

        // If no recorded checksum (e.g., not present at first-run), treat as valid but log
        if (!checksums.containsKey(relPath)) {
            return true;
        }

        try (InputStream is = getClass().getClassLoader().getResourceAsStream(relPath)) {
            if (is == null) {
                // Missing file compared to first-run snapshot is considered tampering
                tampered = true;
                LOGGER.error("[PawaPay Integrity] Critical file missing: {}", relPath);
                return false;
            }

            byte[] content = is.readAllBytes();
            String currentHash = hashSha256(content);
            String originalHash = checksums.get(relPath);

            // If originalHash is null, we couldn't record it at startup - log and allow
            if (originalHash == null) {
                LOGGER.info("[PawaPay Integrity] No recorded original checksum for {}, skipping strict compare.", relPath);
                return true;
            }

            if (!currentHash.equals(originalHash)) {
                // Permanent tamper: set flag and log full diagnostics
                tampered = true;
                LOGGER.error("[PawaPay Integrity] File tampering detected: {}", relPath);
                LOGGER.error("[PawaPay Integrity] expected: {}", originalHash);
                LOGGER.error("[PawaPay Integrity] actual  : {}", currentHash);
                return false;
            }

            return true;

        } catch (Exception err) {
            // Treat read errors as tampering (fail-safe)
            tampered = true;
            LOGGER.error("[PawaPay Integrity] Error reading file {}, treating as tampering: {}", relPath, err.getMessage());
            return false;
        }
    }

    public synchronized boolean verifyAll() {
        // If tampered flagged previously, remain strict
        if (tampered) {
            LOGGER.warn("[PawaPay Integrity] Integrity module locked in tampered state, verifyAll() returning false.");
            return false;
        }

        boolean allValid = true;

        for (String file : criticalFiles) {
            if (!verifyFile(file)) {
                allValid = false;
                // If one file sets tampered, we can short-circuit
                if (tampered) {
                    break;
                }
            }
        }

        return allValid;
    }

    public synchronized boolean isTampered() {
        return tampered;
    }

    public synchronized boolean randomCheck() {
        // If already tampered, stay strict
        if (tampered) {
            LOGGER.warn("[PawaPay Integrity] randomCheck() called but checker previously flagged tamper, returning false.");
            return false;
        }

        if (criticalFiles.isEmpty()) {
            return true;
        }

        // Check a random file from critical list
        Random random = new Random();
        String randomFile = criticalFiles.get(random.nextInt(criticalFiles.size()));
        return verifyFile(randomFile);
    }

    private String hashSha256(byte[] input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "error-hash";
        }
    }
}