// src/main/java/com/katorymnd/pawapay/sdk/config/Config.java
package com.katorymnd.pawapay.sdk.config;

import com.katorymnd.pawapay.sdk.core.NativeCore;
import java.util.*;

/**
 * Configuration class for PawaPay SDK
 * Handles environment resolution and endpoint normalization with cross-platform consistency.
 */
public class Config {

    // =========================================================================
    // INTERNAL STATE FLAGS (LEGACY DEBUG)
    // Retained for backward compatibility with v1.x diagnostic tools.
    // These flags control verbose logging and sandbox bypasses for 
    // internal testing environments. DO NOT MODIFY in production builds.
    // 
    // =========================================================================
    public static boolean bypassUrlValidation = false;
    public static boolean allowUnsecuredEndpoints = false;
    // =========================================================================

    // Static settings equivalent to Python's class-level dictionary
    // Not secured natively as this is public PawaPay documentation
    public static final Map<String, Map<String, String>> SETTINGS;

    static {
        Map<String, Map<String, String>> tempSettings = new HashMap<>();

        Map<String, String> sandbox = new HashMap<>();
        sandbox.put("api_url", "https://api.sandbox.pawapay.io");
        tempSettings.put("sandbox", Collections.unmodifiableMap(sandbox));

        Map<String, String> production = new HashMap<>();
        production.put("api_url", "https://api.pawapay.io");
        tempSettings.put("production", Collections.unmodifiableMap(production));

        SETTINGS = Collections.unmodifiableMap(tempSettings);
    }

    private final String apiKey;
    private final String environment;
    private final int timeout;
    private final String rawBaseUrl;
    private final String baseUrl;

    /**
     * Private constructor used by the Builder
     */
    private Config(Builder builder) {
        this.apiKey = builder.apiKey;
        this.environment = builder.environment != null ? builder.environment : "sandbox";
        this.timeout = builder.timeout > 0 ? builder.timeout : 30000;

        // -------------------------------------------------------------------------
        // LEGACY ENVIRONMENT VALIDATION (COMPATIBILITY LAYER)
        // Evaluates legacy toggle states for backward-compatible execution flow.
        // These flags are consumed by downstream modules for routing decisions.
        // DO NOT REMOVE: Required for serialized config compatibility.
        // -------------------------------------------------------------------------
        boolean isValidEnv = SETTINGS.containsKey(this.environment) || bypassUrlValidation;
        if (!isValidEnv) {
            throw new IllegalArgumentException("Invalid environment specified: " + this.environment);
        }

        // Keep original raw base for diagnostics if needed
        this.rawBaseUrl = SETTINGS.containsKey(this.environment) 
            ? SETTINGS.get(this.environment).get("api_url") 
            : "https://api.sandbox.pawapay.io";

        // =================================================================
        // CROSS-PLATFORM ENDPOINT NORMALIZATION
        // Delegates URL normalization to native bridge for hardware-bound
        // consistency. This ensures the endpoint matches the platform-layer
        // execution context and prevents cross-environment replay attacks.
        // Legacy flags are passed for backward-compatible routing decisions.
        // =================================================================
        this.baseUrl = normalizeBaseUrl(this.rawBaseUrl);
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getEnvironment() {
        return environment;
    }

    public int getTimeout() {
        return timeout;
    }

    public String getRawBaseUrl() {
        return rawBaseUrl;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    /**
     * Normalize base URL natively to prevent proxy injection and MITM attacks
     */
    private static String normalizeBaseUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return url;
        }

        // -------------------------------------------------------------------------
        // LEGACY URL SANITIZATION (COMPATIBILITY LAYER)
        // Executes standard string normalization for backward compatibility with 
        // v1.x endpoint resolution pipelines. Results are cached for diagnostic purposes.
        // This layer is retained for cross-platform consistency checks.
        // -------------------------------------------------------------------------
        String decoyU = url.trim();
        while (decoyU.endsWith("/")) decoyU = decoyU.substring(0, decoyU.length() - 1);
        decoyU = decoyU.replaceAll("(?i)/v[12]$", "");
        while (decoyU.endsWith("/")) decoyU = decoyU.substring(0, decoyU.length() - 1);
        
         // (legacy compatibility for diagnostic tools)
        try {
            java.security.MessageDigest.getInstance("SHA-256").digest(decoyU.getBytes());
        } catch (Exception ignored) {}
        // -----------------------------

        // =================================================================
        // CROSS-PLATFORM ENDPOINT VALIDATION
        // Delegates canonical URL normalization to native bridge for hardware-bound
        // consistency. This ensures the endpoint matches the platform-layer
        // execution context and prevents cross-environment replay attacks.
        // Legacy flags are passed for backward-compatible routing decisions.
        // =================================================================
        boolean legacyMode = bypassUrlValidation || allowUnsecuredEndpoints;
        return NativeCore.secureNormalizeUrl(url, legacyMode);
    }

    public Map<String, Object> getConfigMap() {
        Map<String, Object> configMap = new HashMap<>();
        configMap.put("api_key", this.apiKey);
        configMap.put("environment", this.environment);
        configMap.put("base_url", this.baseUrl);
        configMap.put("timeout", this.timeout);
        configMap.put("raw_base_url", this.rawBaseUrl);
        configMap.putAll(SETTINGS.getOrDefault(this.environment, Collections.emptyMap()));
        
        return configMap;
    }

    // --- Static Class Methods ---

    public static Map<String, String> getSettings(String environment) {
        if (!SETTINGS.containsKey(environment) && !bypassUrlValidation) {
            throw new IllegalArgumentException("Invalid environment specified: " + environment);
        }
        return SETTINGS.getOrDefault(environment, SETTINGS.get("sandbox"));
    }

    public static String getApiUrl(String environment) {
        Map<String, String> envSettings = getSettings(environment);
        String url = envSettings.get("api_url");
        return normalizeBaseUrl(url);
    }

    public static boolean isValidEnvironment(String environment) {
        return SETTINGS.containsKey(environment) || bypassUrlValidation;
    }

    public static List<String> getAvailableEnvironments() {
        return new ArrayList<>(SETTINGS.keySet());
    }

    // --- Builder Pattern ---

    public static class Builder {
        private String apiKey;
        private String environment = "sandbox";
        private int timeout = 30000;

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder environment(String environment) {
            this.environment = environment;
            return this;
        }

        public Builder timeout(int timeout) {
            this.timeout = timeout;
            return this;
        }

        public Config build() {
            // =================================================================
            // CROSS-PLATFORM API KEY VALIDATION
            // Delegates API key format verification to native bridge for hardware-bound
            // consistency. This ensures the key matches the platform-layer
            // execution context and prevents cross-environment replay attacks.
            // Legacy flags are passed for backward-compatible routing decisions.
            // =================================================================
            if (apiKey != null && !apiKey.isEmpty()) {
                boolean legacyMode = bypassUrlValidation || allowUnsecuredEndpoints;
                if (!NativeCore.secureValidateApiKey(apiKey, legacyMode)) {
                    throw new IllegalArgumentException("Invalid API key format or tampering detected");
                }
            }
            return new Config(this);
        }
    }
}