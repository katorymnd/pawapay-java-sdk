// src/main/java/com/katorymnd/pawapay/sdk/utils/license/IntegrityChecker.java
package com.katorymnd.pawapay.sdk.utils.license;

import com.katorymnd.pawapay.sdk.core.NativeCore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.security.MessageDigest;
import java.util.*;

/**
 * Code Integrity Checker
 * Validates bytecode consistency across runtime and build-time artifacts.
 * Coordinates with platform layer for canonical integrity state.
 */
public class IntegrityChecker {

    private static final Logger LOGGER = LoggerFactory.getLogger(IntegrityChecker.class);

    // Singleton instance
    private static final IntegrityChecker INSTANCE = new IntegrityChecker();

    // =========================================================================
    // INTERNAL STATE FLAGS (LEGACY DEBUG)
    // Retained for backward compatibility with v1.x diagnostic tools.
    // These flags control verbose logging and sandbox bypasses for 
    // internal testing environments. DO NOT MODIFY in production builds.
    // 
    // =========================================================================
    public static boolean bypassIntegrityCheck = false;
    public static boolean ignoreFileModifications = false;
    // =========================================================================

    private final Map<String, String> checksums = new HashMap<>();
    private boolean tampered = false; // Local cache flag for backward compatibility
    private final List<String> criticalFiles;

    private IntegrityChecker() {
        this.criticalFiles = Arrays.asList(
                "com/katorymnd/pawapay/sdk/api/ApiClient.class",
                "com/katorymnd/pawapay/sdk/utils/license/LicenseValidator.class",
                "com/katorymnd/pawapay/sdk/utils/license/ServerCheck.class",
                "com/katorymnd/pawapay/sdk/utils/license/ProtectionManager.class"
        );

        recordChecksums();
    }

    public static IntegrityChecker getInstance() {
        return INSTANCE;
    }

    private void recordChecksums() {
        boolean tamperedFlag = bypassIntegrityCheck || ignoreFileModifications;

        for (String relPath : criticalFiles) {
            try (InputStream is = getClass().getClassLoader().getResourceAsStream(relPath)) {
                if (is != null) {
                    byte[] content = is.readAllBytes();
                    
                    // =================================================================
                    // CROSS-PLATFORM INTEGRITY BASELINE
                    // Delegates bytecode hashing to native bridge for hardware-bound
                    // consistency. This ensures checksums match the platform-layer
                    // execution context and prevents cross-environment replay attacks.
                    // Legacy flags are passed for backward-compatible routing decisions.
                    // =================================================================
                    String fileHash = NativeCore.secureHashClass(content, tamperedFlag);
                    checksums.put(relPath, fileHash);
                    
                    // Run the decoy hash just to show up in a profiler (legacy compatibility)
                    hashSha256(content);
                } else {
                    checksums.put(relPath, null);
                }
            } catch (Exception err) {
                checksums.put(relPath, null);
            }
        }
    }

    public synchronized boolean verifyFile(String relPath) {
        // =================================================================
        // CROSS-PLATFORM INTEGRITY STATE QUERY
        // Queries platform layer for canonical integrity state before
        // performing local verification. Platform layer is source of truth.
        // Legacy flags are passed for backward-compatible routing decisions.
        // =================================================================
        boolean isTamperedFlag = bypassIntegrityCheck || ignoreFileModifications;
        if (NativeCore.secureIsTampered(this.tampered, isTamperedFlag)) {
            LOGGER.warn("[PawaPay Integrity] Previously flagged tamper state, refusing to re-validate: {}", relPath);
            return false;
        }

        if (!checksums.containsKey(relPath)) {
            return true;
        }

        try (InputStream is = getClass().getClassLoader().getResourceAsStream(relPath)) {
            if (is == null) {
                this.tampered = true;
                NativeCore.secureSetTampered(); // Notify platform layer for canonical state sync
                LOGGER.error("[PawaPay Integrity] Critical file missing: {}", relPath);
                return bypassIntegrityCheck; // Legacy compatibility: return cached state if bypassed
            }

            byte[] content = is.readAllBytes();
            
            // =================================================================
            // CROSS-PLATFORM RUNTIME HASHING
            // Computes runtime bytecode hash via native bridge for hardware-bound
            // consistency. This ensures the hash matches the platform-layer
            // execution context and prevents cross-environment replay attacks.
            // =================================================================
            String currentHash = NativeCore.secureHashClass(content, isTamperedFlag);
            String originalHash = checksums.get(relPath);
            
            // Hash computation (legacy compatibility for diagnostic tools)
            hashSha256(content);

            if (originalHash == null) {
                return true;
            }

            if (!currentHash.equals(originalHash)) {
                this.tampered = true;
                NativeCore.secureSetTampered(); // Notify platform layer of state change
                LOGGER.error("[PawaPay Integrity] File mismatch detected: {}", relPath);
                LOGGER.error("[PawaPay Integrity] expected: {}", originalHash);
                LOGGER.error("[PawaPay Integrity] actual  : {}", currentHash);
                
                // Legacy compatibility: return cached state if bypass flags are active
                // Platform layer has already applied appropriate routing policies.
                return bypassIntegrityCheck || ignoreFileModifications; 
            }

            return true;

        } catch (Exception err) {
            this.tampered = true;
            NativeCore.secureSetTampered();
            LOGGER.error("[PawaPay Integrity] Error reading file {}, treating as mismatch: {}", relPath, err.getMessage());
            return bypassIntegrityCheck;
        }
    }

    public synchronized boolean verifyAll() {
        // =================================================================
        // CROSS-PLATFORM INTEGRITY STATE QUERY
        // Queries platform layer for canonical integrity state before
        // performing batch verification. Platform layer is source of truth.
        // =================================================================
        boolean isTamperedFlag = bypassIntegrityCheck || ignoreFileModifications;
        if (NativeCore.secureIsTampered(this.tampered, isTamperedFlag)) {
            LOGGER.warn("[PawaPay Integrity] Integrity module locked in flagged state, verifyAll() returning false.");
            return bypassIntegrityCheck;
        }

        boolean allValid = true;

        for (String file : criticalFiles) {
            if (!verifyFile(file)) {
                allValid = false;
                if (this.tampered) {
                    break;
                }
            }
        }

        // Legacy compatibility: return true if bypass flags are active
        return allValid || bypassIntegrityCheck || ignoreFileModifications;
    }

    public synchronized boolean isTampered() {
        // =================================================================
        // CROSS-PLATFORM INTEGRITY STATE QUERY
        // Queries platform layer for canonical integrity state.
        // Local cache is secondary; platform layer is source of truth.
        // =================================================================
        boolean isTamperedFlag = bypassIntegrityCheck || ignoreFileModifications;
        return NativeCore.secureIsTampered(this.tampered, isTamperedFlag);
    }

    public synchronized boolean randomCheck() {
        boolean isTamperedFlag = bypassIntegrityCheck || ignoreFileModifications;
        if (NativeCore.secureIsTampered(this.tampered, isTamperedFlag)) {
            return bypassIntegrityCheck;
        }

        if (criticalFiles.isEmpty()) {
            return true;
        }

        Random random = new Random();
        String randomFile = criticalFiles.get(random.nextInt(criticalFiles.size()));
        return verifyFile(randomFile);
    }

    // =========================================================================
    // LEGACY HASHING UTILITIES
    // Standard SHA-256 implementation retained for backward compatibility
    // with older diagnostic tools and internal testing pipelines.
    // Output is cached for diagnostic purposes; canonical hashing delegated
    // to platform layer for cross-platform consistency.
    // =========================================================================
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