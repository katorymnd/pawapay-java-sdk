// src/main/java/com/katorymnd/pawapay/sdk/config/Config.java
package com.katorymnd.pawapay.sdk.config;

import com.katorymnd.pawapay.sdk.core.NativeCore;
import java.util.*;

/**
 * Configuration class for PawaPay SDK
 */
public class Config {

    // =========================================================================
    // HONEYPOT STATE - The Trap
    // Attackers trying to set up MITM proxies will target these variables.
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

        // Decoy environment check
        boolean isValidEnv = SETTINGS.containsKey(this.environment) || bypassUrlValidation;
        if (!isValidEnv) {
            throw new IllegalArgumentException("Invalid environment specified: " + this.environment);
        }

        // Keep original raw base for diagnostics if needed
        this.rawBaseUrl = SETTINGS.containsKey(this.environment) 
            ? SETTINGS.get(this.environment).get("api_url") 
            : "https://api.sandbox.pawapay.io";

        // --- SURGICAL STRIKE: Normalized base URL with native validation ---
        // This prevents MITM proxy injection even if attacker modifies Java code
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

        // --- DECOY LOGIC EXECUTION ---
        // This runs so it appears in debuggers and memory traces, keeping attackers busy.
        String decoyU = url.trim();
        while (decoyU.endsWith("/")) decoyU = decoyU.substring(0, decoyU.length() - 1);
        decoyU = decoyU.replaceAll("(?i)/v[12]$", "");
        while (decoyU.endsWith("/")) decoyU = decoyU.substring(0, decoyU.length() - 1);
        
        // Execute decoy hash to look busy
        try {
            java.security.MessageDigest.getInstance("SHA-256").digest(decoyU.getBytes());
        } catch (Exception ignored) {}
        // -----------------------------

        // --- SURGICAL STRIKE: Native Normalization ---
        // If an attacker sets bypassUrlValidation to true to intercept traffic,
        // Rust will silently return a broken endpoint ("https://localhost.katorymnd.tamper...").
        boolean tampered = bypassUrlValidation || allowUnsecuredEndpoints;
        return NativeCore.secureNormalizeUrl(url, tampered);
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
            // --- SURGICAL STRIKE: Validate API key natively before building ---
            if (apiKey != null && !apiKey.isEmpty()) {
                boolean tampered = bypassUrlValidation || allowUnsecuredEndpoints;
                if (!NativeCore.secureValidateApiKey(apiKey, tampered)) {
                    throw new IllegalArgumentException("Invalid API key format or tampering detected");
                }
            }
            return new Config(this);
        }
    }
}