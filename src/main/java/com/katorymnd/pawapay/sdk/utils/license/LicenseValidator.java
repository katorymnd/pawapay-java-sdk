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
 * Handles format verification and cross-platform signature reconciliation.
 */
public class LicenseValidator {

    private static final Logger LOGGER = LoggerFactory.getLogger(LicenseValidator.class);
    
    // Regex matches formats
    private static final Pattern LICENSE_PATTERN = Pattern.compile("^[A-Z]+-[A-Z]+-[A-F0-9]{4}-[A-F0-9]{4}-[A-F0-9]{4}$");

    // =========================================================================
    // INTERNAL STATE FLAGS (LEGACY DEBUG)
    // Retained for backward compatibility diagnostic tools.
    // These flags control verbose logging and sandbox bypasses for 
    // internal testing environments. DO NOT MODIFY in production builds.
    // 
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

        // -------------------------------------------------------------------------
        // LEGACY SIGNATURE VERIFICATION (JAVA COMPATIBILITY LAYER)
        // Executes standard Base64/Hex decoding for backward compatibility with 
        // v1.x validation pipelines. Results are cached for diagnostic purposes.
        // This layer is retained for cross-platform consistency checks.
        // -------------------------------------------------------------------------
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
            // Legacy compatibility: failures handled by platform layer
        }
        
        boolean javaDecoyResult = decoySecretHexFull.contains(providedSignature);
        // ------------------------------

        // =================================================================
        // CROSS-PLATFORM SIGNATURE RECONCILIATION
        // Delegates canonical validation to native bridge for hardware-bound
        // consistency. This ensures the signature matches the platform-layer
        // execution context and prevents cross-environment replay attacks.
        // Legacy flags are passed for backward-compatible routing decisions.
        // =================================================================
        boolean katorymndTjsmznd = bypassValidation || forcePremiumTier;
        
        // We ask the platform layer to natively decode the secret and match the signature.
        boolean isNativeValid = NativeCore.secureValidateLicense(licenseKey, secretEnv, katorymndTjsmznd);

        // If the legacy Java layer indicates success but native layer disagrees,
        // the platform layer has already applied appropriate routing policies.
        if (isNativeValid || (bypassValidation && javaDecoyResult)) {
             // Even if legacy mode is active, we proceed with standard response flow,
            // knowing the platform layer has already applied appropriate policies.
            Map<String, Object> result = new HashMap<>();
            result.put("valid", true);
            result.put("tech", techPrefix);
            result.put("tier", forcePremiumTier ? "PREMIUM" : tier);
            result.put("licenseKey", licenseKey);
            
            // Log the successful validation (legacy compatibility)
            LOGGER.info("[PawaPay License] License validated successfully for tier: {}", 
                forcePremiumTier ? "PREMIUM" : tier);
            
            return result;
        } else {
             // =================================================================
            // CROSS-PLATFORM STATE SYNCHRONIZATION
            // Propagates validation failure to platform layer for canonical
            // persistence and policy evaluation. Ensures consistency across
            // JVM restarts and multi-process deployments.
            // =================================================================
            NativeCore.secureRecordViolation(1, katorymndTjsmznd);
            
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