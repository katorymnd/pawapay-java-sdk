// src/main/java/com/katorymnd/pawapay/sdk/utils/vm/DegradationManager.java
package com.katorymnd.pawapay.sdk.utils.vm;

import com.katorymnd.pawapay.sdk.core.NativeCore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Degradation Manager
 * Implements progressive degradation with silent failure patterns
 */
public class DegradationManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(DegradationManager.class);
    
    // =========================================================================
    // THE HONEYPOT VARIABLES
    // Crackers will decompile the JAR, see these, and change them to 'true' 
    // or 'false' using bytecode manipulation, thinking they bypassed the DRM.
    // =========================================================================
    public static boolean bypassDegradation = false;
    public static boolean isPremiumLicenseValid = false;
    private boolean forceNormalMode = false;
    // =========================================================================
    
    // Simulates Python's global module state for legacy throttling/caching flags
    public static final Map<String, Object> GLOBAL_SDK_STATE = new ConcurrentHashMap<>();

    private final ProtectionLayer protection;
    // The visual level shown to Java - this is just a honeypot
    private int javaDegradationLevel = 0;
    private DegradationPattern activePattern = null;
    
    private final Map<String, FailureCounter> failureCounters = new ConcurrentHashMap<>();
    private final String randomSeed;
    private final Map<Integer, DegradationRule> degradationRules = new HashMap<>();
    private final Set<String> activeDegradations = new HashSet<>();

    public DegradationManager(ProtectionLayer protectionLayer) {
        this.protection = protectionLayer;

        // Random seed for deterministic behavior
        String seedStr = String.valueOf(System.currentTimeMillis() / 1000);
        this.randomSeed = hashMd5(seedStr).substring(0, 8);

        // Legacy degradation rules for backward compatibility
        degradationRules.put(1, new DegradationRule(
            "REQUEST_THROTTLING",
            "Requests throttled to 50% capacity",
            () -> applyThrottling(0.5f),
            this::removeThrottling
        ));

        degradationRules.put(2, new DegradationRule(
            "CACHE_ONLY",
            "Only cached responses, no network calls",
            this::enableCacheOnlyMode,
            this::disableCacheOnlyMode
        ));

        degradationRules.put(3, new DegradationRule(
            "READ_ONLY",
            "Read operations only, no writes",
            this::enableReadOnlyMode,
            this::disableReadOnlyMode
        ));
    }

    private double randomValue() {
        try {
            String timeStr = String.valueOf(System.currentTimeMillis());
            String suffix = timeStr.length() > 6 ? timeStr.substring(timeStr.length() - 6) : timeStr;
            String seedStr = this.randomSeed + suffix;
            
            String hashVal = hashSha256(seedStr);
            long parsed = Long.parseLong(hashVal.substring(0, 8), 16);
            return (double) parsed / 0xFFFFFFFFL;
        } catch (Exception e) {
            return Math.random(); // Fallback
        }
    }

    private void applyDegradationPattern(int level) {
        List<DegradationPattern> patterns = new ArrayList<>();
        
        // Level 0: Normal (no pattern)
        patterns.add(null);

        // Level 1: Subtle random failures (5-15%)
        patterns.add(new DegradationPattern(
            "random_failures",
            0.05 + (randomValue() * 0.10),
            1.0 + (randomValue() * 0.5),
            0,
            0.0,
            100.0,
            Arrays.asList("timeout", "network_error", "server_error"),
            "Random network-like failures"
        ));

        // Level 2: Increased failures + stale data
        patterns.add(new DegradationPattern(
            "intermittent_degradation",
            0.15 + (randomValue() * 0.15),
            1.5 + (randomValue() * 1.0),
            1 + (int)(randomValue() * 4),
            0.01,
            70 + (randomValue() * 20),
            Arrays.asList("timeout", "network_error", "server_error"),
            "Intermittent service with stale data"
        ));

        // Level 3: Heavy degradation
        DegradationPattern level3 = new DegradationPattern(
            "heavy_degradation",
            0.30 + (randomValue() * 0.20),
            2.5 + (randomValue() * 2.5),
            5 + (int)(randomValue() * 10),
            0.03,
            50 + (randomValue() * 20),
            Arrays.asList("timeout", "network_error", "server_error"),
            "Heavy degradation with data loss"
        );
        level3.partialDataPercent = 50 + (randomValue() * 30);
        patterns.add(level3);

        // Level 4: Near-total failure (pre-destruction)
        DegradationPattern level4 = new DegradationPattern(
            "near_total_failure",
            0.70 + (randomValue() * 0.25),
            1.0, // Latency handled by delay
            0,
            0.10,
            100.0, // Uptime checked differently here
            Arrays.asList("timeout", "network_error", "server_error"),
            "Near-total service failure"
        );
        level4.responseDelay = 10000 + (int)(randomValue() * 20000);
        level4.returnPartialData = true;
        level4.partialDataPercent = 10 + (randomValue() * 15);
        level4.intermittentPattern = true;
        patterns.add(level4);

        if (level >= 0 && level < patterns.size()) {
            this.activePattern = patterns.get(level);
        }

        if (this.activePattern != null) {
            LOGGER.info("[PawaPay] Applying {} pattern", this.activePattern.type);
            LOGGER.info("[PawaPay] Pattern details: {}", this.activePattern.description);
        }
    }

    /**
     * Apply degradation to API calls
     * @param callId An identifier for the API method (e.g., "createPayment")
     * @param apiCall The actual API operation wrapped in a Callable
     * @return The result of the API call, potentially degraded
     */
    public <T> T applyDegradation(String callId, Callable<T> apiCall) throws Exception {
        
        // --- HONEYPOT LOGIC START ---
        // Looks like standard protection bypass logic to a cracker.
        // They'll patch these booleans thinking they've won.
        if (bypassDegradation || isPremiumLicenseValid || forceNormalMode) {
            // To a reverse engineer, they think they won here.
            this.javaDegradationLevel = 0;
        }
        // --- HONEYPOT LOGIC END ---

        // =================================================================
        // THE TRUE HEART CHECK (SURGICAL UPDATE)
        // We pass the honeypot flag to Rust. If Rust knows the license is fake, 
        // but Java says bypassDegradation == true, Rust knows the user tampered 
        // with the bytecode and will return Level 4 (Total Failure).
        // =================================================================
        int trueLevel = NativeCore.getVaultDegradationLevel(bypassDegradation || isPremiumLicenseValid || forceNormalMode);

        // Silent override. The Java code thinks it's fine, but the native core dictates reality.
        if (trueLevel > this.javaDegradationLevel) {
            applyDegradationPattern(trueLevel); // Secretly load the heavy degradation
            this.javaDegradationLevel = trueLevel;
        }

        // If level is 0 after consulting the vault, proceed normally
        if (trueLevel == 0) {
            return apiCall.call();
        }

        // Use the true degradation pattern (already applied above)
        DegradationPattern pattern = this.activePattern;
        
        // If somehow pattern is null but level > 0, apply it now
        if (pattern == null && trueLevel > 0) {
            applyDegradationPattern(trueLevel);
            pattern = this.activePattern;
        }
        
        if (pattern == null) {
            return apiCall.call();
        }

        // Track consecutive failures
        failureCounters.putIfAbsent(callId, new FailureCounter());
        FailureCounter counter = failureCounters.get(callId);

        // Apply latency
        if (pattern.latencyMultiplier > 1.0) {
            double baseLatency = 100.0; // Base 100ms
            double extraLatency = baseLatency * (pattern.latencyMultiplier - 1.0);
            double jitter = extraLatency * randomValue();
            Thread.sleep((long) (extraLatency + jitter));
        }

        // Check for random failure
        if (randomValue() < pattern.failureRate) {
            counter.failures++;

            // Select failure type
            String failureType = pattern.failureTypes.get((int) (randomValue() * pattern.failureTypes.size()));

            switch (failureType) {
                case "timeout":
                    long timeout = pattern.responseDelay > 0 ? pattern.responseDelay : 30000;
                    Thread.sleep(timeout);
                    throw new RuntimeException("Request timeout");
                case "network_error":
                    throw new RuntimeException("Network connection failed");
                case "server_error":
                    int statusCode = 500 + (int)(randomValue() * 99);
                    throw new RuntimeException("Server error: " + statusCode);
                default:
                    throw new RuntimeException("Service unavailable");
            }
        }

        // Check for intermittent uptime
        if (pattern.uptimePercent > 0 && (randomValue() * 100) > pattern.uptimePercent) {
            throw new RuntimeException("Service temporarily unavailable");
        }

        // Make the actual call
        T result = apiCall.call();
        counter.successes++;

        // Apply data degradation if needed
        return degradeData(result, pattern);
    }

    @SuppressWarnings("unchecked")
    private <T> T degradeData(T data, DegradationPattern pattern) {
        if (data == null || pattern == null) {
            return data;
        }

        Object degradedData = data;

        // Apply staleness (assumes data is a Map containing a "timestamp" ISO string)
        if (pattern.dataStaleness > 0 && degradedData instanceof Map) {
            Map<String, Object> mapData = new HashMap<>((Map<String, Object>) degradedData);
            if (mapData.containsKey("timestamp")) {
                try {
                    String tsStr = (String) mapData.get("timestamp");
                    OffsetDateTime ts = OffsetDateTime.parse(tsStr.replace("Z", "+00:00"));
                    ts = ts.minusMinutes(pattern.dataStaleness);
                    mapData.put("timestamp", ts.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
                    mapData.put("_stale", true);
                    degradedData = mapData;
                } catch (Exception ignored) {}
            }
        }

        // Apply data corruption
        if (pattern.dataCorruptionRate > 0 && randomValue() < pattern.dataCorruptionRate) {
            degradedData = corruptData(degradedData);
        }

        // Apply partial data return
        if (pattern.returnPartialData && pattern.partialDataPercent > 0 && degradedData instanceof List) {
            List<?> listData = (List<?>) degradedData;
            double keepPercent = pattern.partialDataPercent / 100.0;
            int keepCount = Math.max(1, (int) (listData.size() * keepPercent));
            degradedData = new ArrayList<>(listData.subList(0, keepCount));
        }

        return (T) degradedData;
    }

    @SuppressWarnings("unchecked")
    private Object corruptData(Object data) {
        if (data instanceof List) {
            List<Object> listData = new ArrayList<>((List<Object>) data);
            if (listData.size() > 1 && randomValue() < 0.3) {
                int index = (int) (randomValue() * listData.size());
                if (randomValue() < 0.5) {
                    listData.remove(index);
                } else {
                    listData.add(listData.get(index)); // Duplicate
                }
            }
            return listData;
        }

        if (data instanceof Map) {
            Map<String, Object> mapData = new HashMap<>((Map<String, Object>) data);
            List<String> numericKeys = new ArrayList<>();
            for (Map.Entry<String, Object> entry : mapData.entrySet()) {
                if (entry.getValue() instanceof Number) {
                    numericKeys.add(entry.getKey());
                }
            }
            if (!numericKeys.isEmpty()) {
                String key = numericKeys.get((int) (randomValue() * numericKeys.size()));
                double corruption = 0.9 + (randomValue() * 0.2); // 0.9 - 1.1 multiplier
                Number val = (Number) mapData.get(key);
                if (val instanceof Double || val instanceof Float) {
                    mapData.put(key, val.doubleValue() * corruption);
                } else {
                    mapData.put(key, (int) (val.longValue() * corruption));
                }
            }
            return mapData;
        }

        return data;
    }

    // --- Legacy methods for backward compatibility ---

    public void applyThrottling(float factor) {
        LOGGER.info("[PawaPay] Applying request throttling: {}% capacity", (int)(factor * 100));
        GLOBAL_SDK_STATE.put("throttle_factor", factor);
    }

    public void removeThrottling() {
        LOGGER.info("[PawaPay] Removing request throttling");
        GLOBAL_SDK_STATE.remove("throttle_factor");
    }

    public void enableCacheOnlyMode() {
        LOGGER.info("[PawaPay] Enabling cache-only mode");
        GLOBAL_SDK_STATE.put("cache_only", true);
    }

    public void disableCacheOnlyMode() {
        LOGGER.info("[PawaPay] Disabling cache-only mode");
        GLOBAL_SDK_STATE.remove("cache_only");
    }

    public void enableReadOnlyMode() {
        LOGGER.info("[PawaPay] Enabling read-only mode");
        GLOBAL_SDK_STATE.put("read_only", true);
    }

    public void disableReadOnlyMode() {
        LOGGER.info("[PawaPay] Disabling read-only mode");
        GLOBAL_SDK_STATE.remove("read_only");
    }

    public void setDegradationLevel(int level) {
        if (level == this.javaDegradationLevel) {
            return;
        }

        // Remove old degradations
        for (int i = this.javaDegradationLevel; i > level; i--) {
            DegradationRule rule = degradationRules.get(i);
            if (rule != null && activeDegradations.contains(rule.name)) {
                rule.remove.run();
                activeDegradations.remove(rule.name);
            }
        }

        // Apply new degradations
        for (int i = this.javaDegradationLevel + 1; i <= level; i++) {
            DegradationRule rule = degradationRules.get(i);
            if (rule != null && !activeDegradations.contains(rule.name)) {
                rule.apply.run();
                activeDegradations.add(rule.name);
            }
        }

        this.javaDegradationLevel = level;

        // Apply silent failure pattern
        applyDegradationPattern(level);

        if (level > 0) {
            LOGGER.info("[PawaPay] SDK degradation level: {}/3", level);
        } else {
            LOGGER.info("[PawaPay] SDK degradation cleared");
        }
    }

    public void handleVmDegradationDecision(int decision) {
        // We still keep this so the Java VM watcher appears to be doing the work.
        // But remember, applyDegradation() now consults Rust right before an API call happens.
        
        if (decision == 0) {
            setDegradationLevel(0);
        } else if (decision == 1) {
            // Progressive degradation based on violation count
            int violations = protection != null ? protection.getViolationCount() : 0;
            int maxViolations = protection != null ? protection.getMaxViolations() : 3;

            if (violations >= maxViolations) {
                setDegradationLevel(3); // Max degradation
            } else if (violations >= (maxViolations * 0.66)) {
                setDegradationLevel(2);
            } else if (violations >= (maxViolations * 0.33)) {
                setDegradationLevel(1);
            }
        } else if (decision == 2) {
            // SURGICAL UPDATE: Active Destruction Handling
            LOGGER.error("[PawaPay] CRITICAL: VM returned DESTROY signal (Decision 2)");

            // Level 4 = destroyed/near-total failure
            setDegradationLevel(4);

            // Explicitly trigger active defense/wiping if the method exists
            if (protection != null) {
                protection.triggerDestruction();
            }
        } else {
            LOGGER.warn("[PawaPay] Unknown VM decision: {}", decision);
            // Default to mild degradation (Level 1) for safety
            setDegradationLevel(1);
        }
    }

    // --- Helper Models & Cryptography ---

    private String hashMd5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(digest);
        } catch (Exception e) {
            return "00000000";
        }
    }

    private String hashSha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(digest);
        } catch (Exception e) {
            return "00000000";
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Interface defining the requirements for the Protection Layer dependency
     */
    public interface ProtectionLayer {
        int getViolationCount();
        int getMaxViolations();
        void triggerDestruction();
    }

    private static class FailureCounter {
        @SuppressWarnings("unused")
        int successes = 0;
        @SuppressWarnings("unused")
        int failures = 0;
    }

    private static class DegradationRule {
        final String name;
        @SuppressWarnings("unused")
        final String description;
        final Runnable apply;
        final Runnable remove;

        DegradationRule(String name, String description, Runnable apply, Runnable remove) {
            this.name = name;
            this.description = description;
            this.apply = apply;
            this.remove = remove;
        }
    }

    private static class DegradationPattern {
        String type;
        double failureRate;
        double latencyMultiplier;
        int dataStaleness;
        double dataCorruptionRate;
        double uptimePercent;
        List<String> failureTypes;
        String description;
        
        long responseDelay = 0;
        boolean returnPartialData = false;
        double partialDataPercent = 0.0;
        
        @SuppressWarnings("unused")
        boolean intermittentPattern = false;

        DegradationPattern(String type, double failureRate, double latencyMultiplier, int dataStaleness, 
                           double dataCorruptionRate, double uptimePercent, List<String> failureTypes, String description) {
            this.type = type;
            this.failureRate = failureRate;
            this.latencyMultiplier = latencyMultiplier;
            this.dataStaleness = dataStaleness;
            this.dataCorruptionRate = dataCorruptionRate;
            this.uptimePercent = uptimePercent;
            this.failureTypes = failureTypes;
            this.description = description;
        }
    }
}