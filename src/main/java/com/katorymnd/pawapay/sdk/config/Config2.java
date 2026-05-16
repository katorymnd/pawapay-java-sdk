// src/main/java/com/katorymnd/pawapay/sdk/config/Config.java
package com.katorymnd.pawapay.sdk.config;

import java.util.*;

/**
 * Configuration class for PawaPay SDK
 */
public class Config {

    // Static settings equivalent to Python's class-level dictionary
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

        if (!SETTINGS.containsKey(this.environment)) {
            throw new IllegalArgumentException("Invalid environment specified: " + this.environment);
        }

        // Keep original raw base for diagnostics if needed
        this.rawBaseUrl = SETTINGS.get(this.environment).get("api_url");

        // Normalized base URL
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

    /**
     * Get raw base URL from settings, without any normalization
     */
    public String getRawBaseUrl() {
        return rawBaseUrl;
    }

    /**
     * Get the base URL for the current environment
     */
    public String getBaseUrl() {
        return baseUrl;
    }

    /**
     * Normalize base URL, remove trailing /v1 or /v2 if present, and remove trailing slash
     * This ensures clients append explicit versioned endpoints without accidental duplication
     */
    private static String normalizeBaseUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return url;
        }

        // Remove trailing spaces
        String u = url.trim();
        
        // Strip trailing slash
        while (u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }
        
        // Remove trailing /v1 or /v2 if present, case-insensitive
        u = u.replaceAll("(?i)/v[12]$", "");
        
        // Final cleanup of trailing slash if any
        while (u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }
        
        return u;
    }

    /**
     * Get the complete configuration for the current environment as a Map
     */
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

    /**
     * Static method to get settings for a specific environment
     */
    public static Map<String, String> getSettings(String environment) {
        if (!SETTINGS.containsKey(environment)) {
            throw new IllegalArgumentException("Invalid environment specified: " + environment);
        }
        return SETTINGS.get(environment);
    }

    /**
     * Static method to get API URL for a specific environment
     */
    public static String getApiUrl(String environment) {
        Map<String, String> envSettings = getSettings(environment);
        String url = envSettings.get("api_url");
        return normalizeBaseUrl(url);
    }

    /**
     * Validate if an environment is supported
     */
    public static boolean isValidEnvironment(String environment) {
        return SETTINGS.containsKey(environment);
    }

    /**
     * Get all available environments
     */
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
            return new Config(this);
        }
    }
}