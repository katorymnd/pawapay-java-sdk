// src/main/java/com/katorymnd/pawapay/sdk/utils/Helpers.java
package com.katorymnd.pawapay.sdk.utils;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Utility helper functions for PawaPay SDK
 */
public class Helpers {
    
    private static final java.util.logging.Logger LOGGER = 
        java.util.logging.Logger.getLogger(Helpers.class.getName());
    
    private Helpers() {
        // Private constructor to prevent instantiation
    }
    
    /**
     * Generate a valid UUID version 4
     *
     * @return UUID v4 string
     */
    public static String generateUniqueId() {
        try {
            return UUID.randomUUID().toString();
        } catch (Exception err) {
            LOGGER.warning(String.format("UUID.randomUUID() failed, using fallback: %s", err.getMessage()));
            // Fallback method: Manual UUID v4 generation
            return generateFallbackUuid();
        }
    }
    
    /**
     * Alternative method for more cryptographically secure UUIDs
     * This is an alias for generateUniqueId() for backward compatibility
     *
     * @return UUID v4 string
     */
    public static String generateSecureUniqueId() {
        return generateUniqueId();
    }
    
    /**
     * Validate if a string is a valid UUID v4
     *
     * @param uuidStr The UUID to validate
     * @return true if valid UUID v4
     */
    public static boolean isValidUuid(String uuidStr) {
        if (uuidStr == null) {
            return false;
        }
        
        Pattern uuidv4Regex = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$",
            Pattern.CASE_INSENSITIVE
        );
        
        return uuidv4Regex.matcher(uuidStr).matches();
    }
    
    /**
     * Fallback method for manual UUID v4 generation
     *
     * @return Manually generated UUID v4 string
     */
    private static String generateFallbackUuid() {
        java.util.Random random = new java.util.Random();
        
        // Generate 16 random bytes for UUID
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        
        // Set version (4) and variant (2) bits
        bytes[6] = (byte) ((bytes[6] & 0x0f) | 0x40);  // version 4
        bytes[8] = (byte) ((bytes[8] & 0x3f) | 0x80);  // variant 2
        
        // Convert to UUID string format
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 16; i++) {
            if (i == 4 || i == 6 || i == 8 || i == 10) {
                sb.append('-');
            }
            sb.append(String.format("%02x", bytes[i]));
        }
        
        return sb.toString();
    }
}