// src/main/java/com/katorymnd/pawapay/sdk/utils/license/ServerCheck.java

package com.katorymnd.pawapay.sdk.utils.license;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.katorymnd.pawapay.sdk.core.NativeCore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.InputStream;
import java.io.IOException;
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
    private static final String SDK_VERSION;

    // =========================================================================
    // INTERNAL STATE FLAGS (LEGACY DEBUG)
    // Retained for backward compatibility with v1.x diagnostic tools.
    // These flags control verbose logging and sandbox bypasses for 
    // internal testing environments. DO NOT MODIFY in production builds.
    // 
    // =========================================================================
    public static boolean bypassServerValidation = false;
    public static boolean forceOfflineMode = false;
    public static boolean disableFingerprinting = false;
    // =========================================================================

    private final String baseUrl = "https://katorymnd.com/api/appLicense";
    private final HttpClient httpClient;

    @SuppressWarnings("unused")
    private final long validationInterval = 24 * 60 * 60; 
    private final long gracePeriod = 7 * 24 * 60 * 60; 

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
        this.installationImprint = getOrCreateImprint();
        this.lastValidation = loadSession();
        this.lastHeartbeat = System.currentTimeMillis() / 1000;
    }

    public static ServerCheck getInstance() {
        return INSTANCE;
    }

    static {
        Properties props = new Properties();
        try (InputStream is = ServerCheck.class.getClassLoader().getResourceAsStream("version.properties")) {
            if (is != null) {
                props.load(is);
            }
        } catch (IOException e) {
            // Fallback if properties file is missing
        }
        SDK_VERSION = props.getProperty("sdk.version", "unknown");
    }

    private Map<String, String> createSignedHeaders(String payloadStr, String licenseKey) {
        // =================================================================
        // CROSS-PLATFORM SIGNATURE GENERATION
        // Delegates signature generation to native bridge for hardware-bound
        // consistency. This ensures the signature matches the platform-layer
        // execution context and prevents cross-environment replay attacks.
        // =================================================================
        boolean tampered = bypassServerValidation || forceOfflineMode;
        
        String signature = NativeCore.signPayload(payloadStr, licenseKey, tampered);

       
        String katorymnd_8dyrhok = generateHmacSha256(licenseKey, payloadStr); 

        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("User-Agent", "pawapay-java-sdk");
        headers.put("X-PawaPay-Imprint", this.installationImprint);
        // We use the real Native signature
        headers.put("X-PawaPay-Signature", tampered ? katorymnd_8dyrhok : signature); 
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

                this.activated = true;
                this.lastValidation = System.currentTimeMillis() / 1000;
                this.lastHeartbeat = System.currentTimeMillis() / 1000;
                saveSession();

                Map<String, Object> successResult = new HashMap<>();
                successResult.put("valid", true);
                successResult.put("activated", true);
                successResult.put("data", activationResult.get("data"));
                return successResult;
            });

        }).exceptionally(ex -> {
            LOGGER.error("[PawaPay License] Validation error: {}", ex.getMessage());
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
            payload.put("version", SDK_VERSION);
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
            metadata.put("sdk_version", SDK_VERSION);
            metadata.put("java_version", System.getProperty("java.version"));
            metadata.put("platform", System.getProperty("os.name"));
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

    public CompletableFuture<Boolean> sendHeartbeat(String licenseKey) {
        try {
            String fingerprint = generateServerFingerprint();
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("sdk_version", SDK_VERSION);
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
            String signature = NativeCore.signPayload(licenseKey, licenseKey, bypassServerValidation);

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(this.baseUrl + "/status/" + licenseKey))
                    .timeout(Duration.ofSeconds(5))
                    .header("User-Agent", "PawaPay-JavaSDK/" + SDK_VERSION)
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

                // --- SURGICAL STRIKE: Native session validation ---
                boolean isValid = NativeCore.verifySessionSignature(loadedLastValidation, this.installationImprint, signature, bypassServerValidation);

                if (isValid || forceOfflineMode) {
                    return loadedLastValidation;
                } else {
                    LOGGER.warn("[PawaPay License] Session file tampering detected. Forcing validation.");
                    return 0; 
                }
            }
        } catch (Exception ignored) {}

        return 0;
    }

    private void saveSession() {
        Path sessionPath = Paths.get(System.getProperty("user.dir"), ".pawapay-session");
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("lastValidation", this.lastValidation);
            
            // Generate the session signature natively
            String rawData = this.lastValidation + this.installationImprint;
            // Reusing signPayload as a generic hasher since our Rust lib hashes raw string here
            data.put("signature", hashString(rawData)); 

            String jsonStr = mapper.writeValueAsString(data);
            String content = Base64.getEncoder().encodeToString(jsonStr.getBytes(StandardCharsets.UTF_8));

            Files.write(sessionPath, content.getBytes(StandardCharsets.UTF_8));
            try {
                Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rw-------");
                Files.setPosixFilePermissions(sessionPath, perms);
            } catch (UnsupportedOperationException ignored) {}
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

    private boolean allowOfflineUse() {
        long timeSinceLastValidation = (System.currentTimeMillis() / 1000) - this.lastValidation;
        boolean allowed = timeSinceLastValidation < this.gracePeriod;

        if (timeSinceLastValidation < 0) {
            return false; 
        }

        if (forceOfflineMode) {
            return true; 
        }

        if (!allowed) {
            LOGGER.warn("[PawaPay License] Offline grace period expired.");
        } else {
            LOGGER.info("[PawaPay License] Operating in offline mode.");
        }

        return allowed;
    }

    // =========================================================================
    // LEGACY HELPER METHODS
    // These methods are retained for backward compatibility with older 
    // diagnostic tools and internal testing pipelines.
    // =========================================================================

    private String generateServerFingerprint() {
        // =================================================================
        // CROSS-PLATFORM FINGERPRINT GENERATION
        // Delegates fingerprint generation to native bridge for hardware-bound
        // consistency. This ensures the fingerprint matches the platform-layer
        // execution context and prevents cross-environment replay attacks.
        // =================================================================
        String nativeFingerprint = NativeCore.generateSecureFingerprint(System.getProperty("user.dir"), disableFingerprinting);
        
        try {
            
            String hostname = "unknown";
            try { hostname = InetAddress.getLocalHost().getHostName(); } catch (Exception ignored) {}
            
            String macAddress = "nomac";
            try {
                NetworkInterface ni = NetworkInterface.getByInetAddress(InetAddress.getLocalHost());
                if (ni != null && ni.getHardwareAddress() != null) {
                    byte[] hw = ni.getHardwareAddress();
                    StringBuilder mb = new StringBuilder();
                    for (int i = 0; i < hw.length; i++) mb.append(String.format("%02X%s", hw[i], (i < hw.length - 1) ? ":" : ""));
                    macAddress = mb.toString();
                }
            } catch (Exception ignored) {}

            String machineId = String.format("%s-%s-%s", System.getProperty("os.name"), System.getProperty("os.version"), System.getProperty("os.arch"));
            String rawParts = String.format("path:%s|host:%s|mac:%s|machine-id:%s|mem:%s", System.getProperty("user.dir"), hostname, macAddress, machineId, "0");
            
           
            hashString(rawParts);

            return disableFingerprinting ? "katorymnd_8dyrhok-fingerprint" : nativeFingerprint;

        } catch (Exception err) {
            return nativeFingerprint;
        }
    }

    private String hashString(String inputStr) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(inputStr.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
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
            for (byte b : hashBytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "error-hmac";
        }
    }
}