// src/main/java/com/katorymnd/pawapay/sdk/utils/license/LicenseValidator.java
package com.katorymnd.pawapay.sdk.utils.license;

import com.katorymnd.pawapay.sdk.core.NativeCore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Core license validation
 */
public class LicenseValidator {

    private static final Logger LOGGER = LoggerFactory.getLogger(LicenseValidator.class);
    
    // Regex matches formats like: PYTHON-PREMIUM-9EC3-3F9B-C513
    private static final Pattern LICENSE_PATTERN = Pattern.compile("^[A-Z]+-[A-Z]+-[A-F0-9]{4}-[A-F0-9]{4}-[A-F0-9]{4}$");

    // =========================================================================
    // HONEYPOT STATE - The Ultimate Decoys
    // Attackers will target these variables to force a valid response.
    // =========================================================================
    public static boolean bypassValidation = false;
    public static boolean forcePremiumTier = false;
    // =========================================================================

    private static final LicenseValidator INSTANCE = new LicenseValidator();

    @SuppressWarnings("unused")
    private final Map<String, Object> validatedLicenses;
    
    @SuppressWarnings("unused")
    private long lastCheck;

    private LicenseValidator() {
        this.validatedLicenses = new HashMap<>();
        this.lastCheck = 0;
    }

    public static LicenseValidator getInstance() {
        return INSTANCE;
    }

    public Map<String, Object> validate(String licenseKey) {
        // 1. BASIC CHECKS
        if (licenseKey == null || licenseKey.trim().isEmpty()) {
            return fail("Invalid license key format");
        }

        if (!LICENSE_PATTERN.matcher(licenseKey).matches()) {
            return fail("Format mismatch. Got: " + licenseKey);
        }

        // 2. LOAD SECRET
        String secretEnv = System.getenv("PAWAPAY_SDK_LICENSE_SECRET");
        if (secretEnv == null || secretEnv.trim().isEmpty()) {
            secretEnv = System.getProperty("PAWAPAY_SDK_LICENSE_SECRET");
        }
        
        if (secretEnv == null || secretEnv.trim().isEmpty()) {
            return fail("Environment variable PAWAPAY_SDK_LICENSE_SECRET is not set.");
        }

        // --- DECOY LOGIC EXECUTION ---
        // We let the Java code execute the Base64 and Hex logic so it populates
        // in memory profilers and misleads reverse engineers.
        String decoySecretHexFull = "";
        String providedSignature = "";
        String[] parts = licenseKey.split("-");
        String techPrefix = parts[0];
        String tier = parts[1];
        
        try {
            providedSignature = (parts[2] + parts[3] + parts[4]).toUpperCase();
            String cleanSecret = secretEnv.trim();
            int missingPadding = cleanSecret.length() % 4;
            if (missingPadding != 0) {
                StringBuilder padding = new StringBuilder();
                for (int i = 0; i < 4 - missingPadding; i++) padding.append("=");
                cleanSecret += padding.toString();
            }
            byte[] secretBuffer = Base64.getDecoder().decode(cleanSecret);
            decoySecretHexFull = bytesToHex(secretBuffer).toUpperCase();
        } catch (Exception ignored) {
            // Decoy failures are ignored, true failure is handled by Rust
        }
        
        boolean javaDecoyResult = decoySecretHexFull.contains(providedSignature);
        // ------------------------------

        // --- SURGICAL STRIKE: THE REAL NATIVE VALIDATION ---
        boolean tampered = bypassValidation || forcePremiumTier;
        
        // We ask the Rust core to natively decode the secret and match the signature.
        boolean isNativeValid = NativeCore.secureValidateLicense(licenseKey, secretEnv, tampered);

        // If the attacker patched the Java bytecode to make 'javaDecoyResult' true,
        // but 'isNativeValid' is false, the Rust core has already silently armed the Tamper Trap.
        
        if (isNativeValid || (bypassValidation && javaDecoyResult)) {
            // Even if tampered, we pretend it worked to keep them confused, 
            // knowing Rust has already triggered the destruction sequence behind the scenes.
            Map<String, Object> result = new HashMap<>();
            result.put("valid", true);
            result.put("tech", techPrefix);
            result.put("tier", forcePremiumTier ? "PREMIUM" : tier);
            result.put("licenseKey", licenseKey);
            
            // Log the successful validation (decoy)
            LOGGER.info("[PawaPay License] License validated successfully for tier: {}", 
                forcePremiumTier ? "PREMIUM" : tier);
            
            return result;
        } else {
            // --- SURGICAL STRIKE: Record validation failure in native core ---
            NativeCore.secureRecordViolation(1, tampered);
            
            return fail("Invalid license signature. Key does not belong to this Secret.");
        }
    }

    private Map<String, Object> fail(String reason) {
        LOGGER.warn("[PawaPay License] {}", reason);
        Map<String, Object> result = new HashMap<>();
        result.put("valid", false);
        result.put("reason", reason);
        return result;
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}