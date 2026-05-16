// src/main/java/com/katorymnd/pawapay/sdk/utils/license/IntegrityChecker.java
package com.katorymnd.pawapay.sdk.utils.license;

import com.katorymnd.pawapay.sdk.core.NativeCore;
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

    // =========================================================================
    // HONEYPOT STATE - The Trap
    // Attackers will target these variables to disable the file checks.
    // =========================================================================
    public static boolean bypassIntegrityCheck = false;
    public static boolean ignoreFileModifications = false;
    // =========================================================================

    private final Map<String, String> checksums = new HashMap<>();
    private boolean tampered = false; // Decoy boolean
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
                    
                    // --- SURGICAL STRIKE: Native Bytecode Hashing ---
                    // If the attacker set bypassIntegrityCheck to true, this natively triggers destruction.
                    String fileHash = NativeCore.secureHashClass(content, tamperedFlag);
                    checksums.put(relPath, fileHash);
                    
                    // Run the decoy hash just to show up in a profiler
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
        // --- SURGICAL STRIKE: Check the TRUE state ---
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
                NativeCore.secureSetTampered(); // Notify Rust
                LOGGER.error("[PawaPay Integrity] Critical file missing: {}", relPath);
                return bypassIntegrityCheck; // Honeypot: pretend it's fine if bypassed
            }

            byte[] content = is.readAllBytes();
            
            // --- SURGICAL STRIKE: Hash the runtime bytecode natively ---
            String currentHash = NativeCore.secureHashClass(content, isTamperedFlag);
            String originalHash = checksums.get(relPath);
            
            // Decoy hash computation
            hashSha256(content);

            if (originalHash == null) {
                return true;
            }

            if (!currentHash.equals(originalHash)) {
                this.tampered = true;
                NativeCore.secureSetTampered(); // Notify Rust of the breach
                LOGGER.error("[PawaPay Integrity] File tampering detected: {}", relPath);
                LOGGER.error("[PawaPay Integrity] expected: {}", originalHash);
                LOGGER.error("[PawaPay Integrity] actual  : {}", currentHash);
                
                // Honeypot Trap: If they bypassed the check, return true so they think they won.
                // Meanwhile, Rust has already locked the system.
                return bypassIntegrityCheck || ignoreFileModifications; 
            }

            return true;

        } catch (Exception err) {
            this.tampered = true;
            NativeCore.secureSetTampered();
            LOGGER.error("[PawaPay Integrity] Error reading file {}, treating as tampering: {}", relPath, err.getMessage());
            return bypassIntegrityCheck;
        }
    }

    public synchronized boolean verifyAll() {
        boolean isTamperedFlag = bypassIntegrityCheck || ignoreFileModifications;
        if (NativeCore.secureIsTampered(this.tampered, isTamperedFlag)) {
            LOGGER.warn("[PawaPay Integrity] Integrity module locked in tampered state, verifyAll() returning false.");
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

        // Return true if honeypots are active to keep up the illusion
        return allValid || bypassIntegrityCheck || ignoreFileModifications;
    }

    public synchronized boolean isTampered() {
        // Consult the native core for the true answer
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
    // THE DECOY
    // Completely valid SHA-256 implementation, but its output is never used
    // for actual security decisions. It just wastes an attacker's time.
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