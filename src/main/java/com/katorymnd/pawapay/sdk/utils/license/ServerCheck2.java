// src/main/java/com/katorymnd/pawapay/sdk/utils/license/ServerCheck.java
package com.katorymnd.pawapay.sdk.utils.license;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Katorymnd License Server Integration
 * Validates license with https://katorymnd.com/api/appLicense/
 */
public class ServerCheck {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServerCheck.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final ServerCheck INSTANCE = new ServerCheck();

    private final String baseUrl = "https://katorymnd.com/api/appLicense";
    private final HttpClient httpClient;

    // Configuration
    @SuppressWarnings("unused")
    private final long validationInterval = 24 * 60 * 60; // 24 hours in seconds
    private final long gracePeriod = 7 * 24 * 60 * 60; // 7 days in seconds

    // State
    @SuppressWarnings("unused")
    private boolean activated = false;
    private final String domain;
    private final String installationImprint;
    private long lastValidation;
    private long lastHeartbeat;

    private ServerCheck() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        this.domain = getDomain();

        // 1. Initialize "The Soul" (Imprint)
        this.installationImprint = getOrCreateImprint();

        // 2. Load Persistent Session
        this.lastValidation = loadSession();
        this.lastHeartbeat = System.currentTimeMillis() / 1000;
    }

    public static ServerCheck getInstance() {
        return INSTANCE;
    }

    private Map<String, String> createSignedHeaders(String payloadStr, String licenseKey) {
    String signature = generateHmacSha256(licenseKey, payloadStr);

    Map<String, String> headers = new HashMap<>();
    headers.put("Content-Type", "application/json");
    
    headers.put("User-Agent", "pawapay-java-sdk");
    headers.put("X-PawaPay-Imprint", this.installationImprint);
    headers.put("X-PawaPay-Signature", signature);
    headers.put("Accept", "application/json");
    return headers;
}

    public CompletableFuture<Map<String, Object>> validateAndActivate(String licenseKey) {
        return validateLicense(licenseKey).thenCompose(validationResult -> {
            boolean isValid = Boolean.TRUE.equals(validationResult.get("valid"));
            String reason = (String) validationResult.get("reason");
            Integer status = (Integer) validationResult.get("status");

            boolean needsActivation = !isValid && 
                ("activation_required".equals(reason) || Integer.valueOf(422).equals(status));

            if (!isValid && !needsActivation) {
                return CompletableFuture.completedFuture(validationResult);
            }

            if (needsActivation) {
                LOGGER.info("[PawaPay License] Activation required. Proceeding...");
            }

            return activateDomain(licenseKey).thenApply(activationResult -> {
                if (!Boolean.TRUE.equals(activationResult.get("success"))) {
                    Map<String, Object> failResult = new HashMap<>();
                    failResult.put("valid", false);
                    failResult.put("reason", activationResult.get("message"));
                    return failResult;
                }

                // --- SUCCESS: Update State & Save Session ---
                this.activated = true;
                this.lastValidation = System.currentTimeMillis() / 1000;
                this.lastHeartbeat = System.currentTimeMillis() / 1000;
                saveSession();
                // --------------------------------------------

                Map<String, Object> successResult = new HashMap<>();
                successResult.put("valid", true);
                successResult.put("activated", true);
                successResult.put("data", activationResult.get("data"));
                return successResult;
            });

        }).exceptionally(ex -> {
            LOGGER.error("[PawaPay License] Validation error: {}", ex.getMessage());

            // Offline Check using the LOADED timestamp
            Map<String, Object> fallbackResult = new HashMap<>();
            if (allowOfflineUse()) {
                fallbackResult.put("valid", true);
                fallbackResult.put("offline", true);
            } else {
                fallbackResult.put("valid", false);
                fallbackResult.put("reason", "License validation failed & Offline grace period expired");
            }
            return fallbackResult;
        });
    }

    private CompletableFuture<Map<String, Object>> validateLicense(String licenseKey) {
        try {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("javaVersion", System.getProperty("java.version"));
            metadata.put("platform", System.getProperty("os.name"));
            metadata.put("hostname", this.domain);

            Map<String, Object> payload = new HashMap<>();
            payload.put("license_key", licenseKey);
            payload.put("product", "pawapay-java-sdk");
            payload.put("version", "1.0.0");
            payload.put("metadata", metadata);

            String payloadStr = mapper.writeValueAsString(payload);
            Map<String, String> headers = createSignedHeaders(payloadStr, licenseKey);

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(this.baseUrl + "/validate"))
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(payloadStr));

            headers.forEach(requestBuilder::header);

            return httpClient.sendAsync(requestBuilder.build(), HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        Map<String, Object> result = new HashMap<>();
                        try {
                            String data = response.body();
                            Map<String, Object> jsonMap = data != null && !data.isEmpty() 
                                ? mapper.readValue(data, new TypeReference<Map<String, Object>>() {}) 
                                : new HashMap<>();

                            if (response.statusCode() == 200) {
                                if (Boolean.TRUE.equals(jsonMap.get("valid"))) {
                                    result.put("valid", true);
                                    result.put("data", jsonMap);
                                    result.put("status", 200);
                                } else {
                                    result.put("valid", false);
                                    result.put("reason", jsonMap.getOrDefault("reason", jsonMap.getOrDefault("message", "License validation failed")));
                                    result.put("status", response.statusCode());
                                }
                            } else {
                                result.put("valid", false);
                                result.put("reason", jsonMap.getOrDefault("reason", jsonMap.getOrDefault("message", "HTTP " + response.statusCode())));
                                result.put("status", response.statusCode());
                            }
                        } catch (Exception e) {
                            result.put("valid", false);
                            result.put("reason", "Failed to parse response");
                        }
                        return result;
                    });
        } catch (Exception e) {
            return CompletableFuture.failedFuture(new Exception("License validation setup failed: " + e.getMessage()));
        }
    }

    private CompletableFuture<Map<String, Object>> activateDomain(String licenseKey) {
        try {
            String fingerprint = generateServerFingerprint();

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("sdk_version", "1.0.0");
            metadata.put("java_version", System.getProperty("java.version"));
            metadata.put("platform", System.getProperty("os.name"));
            // Simple ISO-8601 string formatting
            metadata.put("activated_at", java.time.Instant.now().toString()); 

            Map<String, Object> payload = new HashMap<>();
            payload.put("license_key", licenseKey);
            payload.put("domain", this.domain);
            payload.put("server_fingerprint", fingerprint);
            payload.put("installation_imprint", this.installationImprint);
            payload.put("metadata", metadata);

            String payloadStr = mapper.writeValueAsString(payload);
            Map<String, String> headers = createSignedHeaders(payloadStr, licenseKey);

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(this.baseUrl + "/activate-domain"))
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(payloadStr));

            headers.forEach(requestBuilder::header);

            return httpClient.sendAsync(requestBuilder.build(), HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        Map<String, Object> result = new HashMap<>();
                        try {
                            String data = response.body();
                            Map<String, Object> jsonMap = data != null && !data.isEmpty() 
                                ? mapper.readValue(data, new TypeReference<Map<String, Object>>() {}) 
                                : new HashMap<>();

                            if (response.statusCode() == 200 || response.statusCode() == 201) {
                                result.put("success", true);
                                result.put("data", jsonMap);
                            } else {
                                result.put("success", false);
                                result.put("message", jsonMap.getOrDefault("message", "Activation failed"));
                            }
                        } catch (Exception e) {
                            result.put("success", false);
                            result.put("message", "Failed to parse response");
                        }
                        return result;
                    });

        } catch (Exception e) {
            return CompletableFuture.failedFuture(new Exception("Domain activation setup failed: " + e.getMessage()));
        }
    }

    // --- SESSION PERSISTENCE LOGIC ---

    private long loadSession() {
        Path sessionPath = Paths.get(System.getProperty("user.dir"), ".pawapay-session");

        try {
            if (Files.exists(sessionPath)) {
                String raw = new String(Files.readAllBytes(sessionPath), StandardCharsets.UTF_8);
                String decoded = new String(Base64.getDecoder().decode(raw), StandardCharsets.UTF_8);
                Map<String, Object> sessionData = mapper.readValue(decoded, new TypeReference<Map<String, Object>>() {});

                long loadedLastValidation = ((Number) sessionData.get("lastValidation")).longValue();
                String signature = (String) sessionData.get("signature");

                // Anti-Tamper Check: Verify the signature matches our Imprint
                String expectedSig = hashString(loadedLastValidation + this.installationImprint);

                if (expectedSig.equals(signature)) {
                    return loadedLastValidation;
                } else {
                    LOGGER.warn("[PawaPay License] Session file tampering detected. Forcing validation.");
                    return 0; // Force immediate check
                }
            }
        } catch (Exception ignored) {
            // File doesn't exist or is corrupt
        }

        return 0;
    }

    private void saveSession() {
        Path sessionPath = Paths.get(System.getProperty("user.dir"), ".pawapay-session");
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("lastValidation", this.lastValidation);
            data.put("signature", hashString(this.lastValidation + this.installationImprint));

            String jsonStr = mapper.writeValueAsString(data);
            String content = Base64.getEncoder().encodeToString(jsonStr.getBytes(StandardCharsets.UTF_8));

            Files.write(sessionPath, content.getBytes(StandardCharsets.UTF_8));

            // Try to set restricted permissions (Unix-like)
            try {
                Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rw-------");
                Files.setPosixFilePermissions(sessionPath, perms);
            } catch (UnsupportedOperationException ignored) {
                // Windows systems will throw this, safely ignore.
            }
        } catch (Exception err) {
            LOGGER.error("[PawaPay License] Failed to persist session: {}", err.getMessage());
        }
    }

    private String getOrCreateImprint() {
        Path imprintPath = Paths.get(System.getProperty("user.dir"), ".pawapay-imprint");
        try {
            if (Files.exists(imprintPath)) {
                return new String(Files.readAllBytes(imprintPath), StandardCharsets.UTF_8).trim();
            }

            String newImprint = UUID.randomUUID().toString();
            Files.write(imprintPath, newImprint.getBytes(StandardCharsets.UTF_8));

            try {
                Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rw-------");
                Files.setPosixFilePermissions(imprintPath, perms);
            } catch (UnsupportedOperationException ignored) {}

            LOGGER.info("[PawaPay License] Initialized secure installation imprint.");
            return newImprint;

        } catch (Exception err) {
            LOGGER.error("[PawaPay License] Could not access imprint file: {}", err.getMessage());
            return "memory-" + UUID.randomUUID().toString();
        }
    }

    public CompletableFuture<Boolean> sendHeartbeat(String licenseKey) {
        try {
            String fingerprint = generateServerFingerprint();

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("sdk_version", "1.0.0");
            metadata.put("uptime", (System.currentTimeMillis() / 1000) - this.lastHeartbeat);

            Map<String, Object> payload = new HashMap<>();
            payload.put("license_key", licenseKey);
            payload.put("domain", this.domain);
            payload.put("server_fingerprint", fingerprint);
            payload.put("installation_imprint", this.installationImprint);
            payload.put("metadata", metadata);

            String payloadStr = mapper.writeValueAsString(payload);
            Map<String, String> headers = createSignedHeaders(payloadStr, licenseKey);

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(this.baseUrl + "/heartbeat"))
                    .timeout(Duration.ofSeconds(5))
                    .POST(HttpRequest.BodyPublishers.ofString(payloadStr));

            headers.forEach(requestBuilder::header);

            return httpClient.sendAsync(requestBuilder.build(), HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        try {
                            if (response.statusCode() == 200) {
                                String data = response.body();
                                Map<String, Object> result = data != null && !data.isEmpty() 
                                    ? mapper.readValue(data, new TypeReference<Map<String, Object>>() {}) 
                                    : new HashMap<>();

                                if (Boolean.TRUE.equals(result.get("ok"))) {
                                    long now = System.currentTimeMillis() / 1000;
                                    this.lastHeartbeat = now;
                                    this.lastValidation = now;
                                    saveSession();
                                    return true;
                                }
                            }
                        } catch (Exception ignored) {}
                        return false;
                    });
        } catch (Exception e) {
            return CompletableFuture.completedFuture(false);
        }
    }

    public CompletableFuture<Map<String, Object>> checkStatus(String licenseKey) {
        if ("true".equals(System.getenv("PAWAPAY_DEV_MODE")) || "true".equals(System.getProperty("PAWAPAY_DEV_MODE"))) {
            Map<String, Object> devResult = new HashMap<>();
            devResult.put("active", true);
            devResult.put("valid", true);
            return CompletableFuture.completedFuture(devResult);
        }

        try {
            String signature = generateHmacSha256(licenseKey, licenseKey);

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(this.baseUrl + "/status/" + licenseKey))
                    .timeout(Duration.ofSeconds(5))
                    .header("User-Agent", "PawaPay-JavaSDK/1.0.0")
                    .header("X-PawaPay-Imprint", this.installationImprint)
                    .header("X-PawaPay-Signature", signature)
                    .header("Accept", "application/json")
                    .GET();

            return httpClient.sendAsync(requestBuilder.build(), HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        try {
                            String data = response.body();
                            return data != null && !data.isEmpty() 
                                ? mapper.readValue(data, new TypeReference<Map<String, Object>>() {}) 
                                : new HashMap<>();
                        } catch (Exception e) {
                            throw new RuntimeException("Failed to parse status response: " + e.getMessage());
                        }
                    });
        } catch (Exception e) {
            return CompletableFuture.failedFuture(new Exception("Status check setup failed: " + e.getMessage()));
        }
    }

    private String getDomain() {
        String domainStr = System.getenv("PAWAPAY_SDK_LICENSE_DOMAIN");
        if (domainStr == null || domainStr.trim().isEmpty()) {
            domainStr = System.getProperty("PAWAPAY_SDK_LICENSE_DOMAIN");
        }

        if (domainStr != null && !domainStr.trim().isEmpty()) {
            return domainStr.trim();
        }
        throw new IllegalStateException("[PawaPay License] Missing PAWAPAY_SDK_LICENSE_DOMAIN.");
    }

    private boolean allowOfflineUse() {
        long timeSinceLastValidation = (System.currentTimeMillis() / 1000) - this.lastValidation;
        boolean allowed = timeSinceLastValidation < this.gracePeriod;

        if (timeSinceLastValidation < 0) {
            return false; // Anti-tamper check for future dates
        }

        if (!allowed) {
            LOGGER.warn("[PawaPay License] Offline grace period expired.");
        } else {
            LOGGER.info("[PawaPay License] Operating in offline mode.");
        }

        return allowed;
    }

    private String generateServerFingerprint() {
        try {
            // Path locking
            String projectPath = System.getProperty("user.dir");
            LOGGER.info("[PawaPay License] Path Locking active for: {}", projectPath);

            // Hostname
            String hostname = "unknown";
            try {
                hostname = InetAddress.getLocalHost().getHostName();
            } catch (Exception ignored) {}

            // MAC address
            String macAddress = "nomac";
            try {
                InetAddress localHost = InetAddress.getLocalHost();
                NetworkInterface ni = NetworkInterface.getByInetAddress(localHost);
                if (ni != null) {
                    byte[] hardwareAddress = ni.getHardwareAddress();
                    if (hardwareAddress != null) {
                        StringBuilder macBuilder = new StringBuilder();
                        for (int i = 0; i < hardwareAddress.length; i++) {
                            macBuilder.append(String.format("%02X%s", hardwareAddress[i], (i < hardwareAddress.length - 1) ? ":" : ""));
                        }
                        macAddress = macBuilder.toString();
                    }
                }
            } catch (Exception ignored) {}

            String machineId = String.format("%s-%s-%s", 
                System.getProperty("os.name"), 
                System.getProperty("os.version"), 
                System.getProperty("os.arch"));

            String totalMem = "0"; // Static placeholder matching Python

            String rawParts = String.format("path:%s|host:%s|mac:%s|machine-id:%s|mem:%s", 
                projectPath, hostname, macAddress, machineId, totalMem);

            return hashString(rawParts);

        } catch (Exception err) {
            LOGGER.error("[PawaPay License] Failed to generate server fingerprint: {}", err.getMessage());
            String hostname = "unknown";
            try {
                hostname = InetAddress.getLocalHost().getHostName();
            } catch (Exception ignored) {}
            String fallbackRaw = String.format("fallback-host:%s-path:%s", hostname, System.getProperty("user.dir"));
            return hashString(fallbackRaw);
        }
    }

    private String hashString(String inputStr) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(inputStr.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "error-hash";
        }
    }

    private String generateHmacSha256(String key, String data) {
        try {
            Mac sha256Hmac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            sha256Hmac.init(secretKey);
            byte[] hashBytes = sha256Hmac.doFinal(data.getBytes(StandardCharsets.UTF_8));

            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "error-hmac";
        }
    }
}