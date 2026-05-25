package com.katorymnd.pawapay.sdk.core;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class NativeCore {

    static {
        try {
            loadDynamicCore();
        } catch (Exception ex) {
            System.err.println("[Katorymnd PawaPay] CRITICAL: Security core missing or tampered. Halting.");
            System.exit(1);
        }
    }

    private static void loadDynamicCore() throws Exception {
        String osName = System.getProperty("os.name").toLowerCase();
        String osArch = System.getProperty("os.arch").toLowerCase();

        String osDir;
        String prefix = "lib";
        String extension;

        if (osName.contains("win")) {
            osDir = "windows";
            prefix = "";
            extension = ".dll";
        } else if (osName.contains("mac")) {
            osDir = "darwin";
            extension = ".dylib";
        } else if (osName.contains("nix") || osName.contains("nux") || osName.contains("aix")) {
            osDir = "linux";
            extension = ".so";
        } else {
            throw new UnsupportedOperationException("Unsupported OS: " + osName);
        }

        String archDir;
        if (osArch.contains("amd64") || osArch.contains("x86_64")) {
            archDir = "x86_64";
        } else if (osArch.contains("aarch64") || osArch.contains("arm64")) {
            archDir = "aarch64";
        } else {
            throw new UnsupportedOperationException("Unsupported Architecture: " + osArch);
        }

        String filename = prefix + "pawapay" + extension;
        String resourcePath = "/natives/" + osDir + "/" + archDir + "/" + filename;

        System.out.println("[Katorymnd PawaPay] Initializing core engine for architecture: " + osDir + "-" + archDir);

        InputStream in = NativeCore.class.getResourceAsStream(resourcePath);
        if (in == null) {
            throw new RuntimeException("Katorymnd Core Library " + resourcePath + " not found in JAR.");
        }

        File tempFile = File.createTempFile("katorymnd_pawapay_core_", extension);
        tempFile.deleteOnExit(); 

        Files.copy(in, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        in.close();

        System.load(tempFile.getAbsolutePath());
        System.out.println("[Katorymnd PawaPay] Native core engine successfully loaded and engaged.");
    }

    // Native Hooks
    public static native int secureVaultInit(String domain, String secret);
    
    public static native int getVaultDegradationLevel(boolean javaHoneypotFlag);

    public static native String initializeHardwareVault();
    public static native int secureRunInterpreter(int javaResult, String contextJson);

    public static native String deriveSecureKey(String imprint, String projectPath);
    
    public static native String secureEncrypt(String plaintext, String secureKeyHex, boolean isTampered);
    
    public static native String secureDecrypt(String ivHex, String ciphertextHex, String secureKeyHex, boolean isTampered);
    
    public static native String generateSecureFingerprint(String projectPath, boolean isTampered);
    public static native String signPayload(String payload, String secretKey, boolean isTampered);
    public static native boolean verifySessionSignature(long timestamp, String imprint, String expectedSig, boolean isTampered);
    public static native void secureRecordViolation(int javaCount, boolean isTampered);
    public static native void secureRecordSuccess(int javaSuccesses, boolean isTampered);
    public static native int secureGetViolationCount(int javaCount, boolean isTampered);
    public static native boolean secureIsDestroyed(boolean javaDestroyed, boolean isTampered);
    public static native void secureResetViolations();
    public static native boolean secureValidateLicense(String licenseKey, String secretEnv, boolean isTampered);
    public static native String secureHashClass(byte[] classBytes, boolean isTampered);
    public static native void secureSetTampered();
    public static native boolean secureIsTampered(boolean javaTampered, boolean isTamperedFlag);
    public static native String secureNormalizeUrl(String url, boolean isTampered);
    public static native int secureInitializeClient(boolean isTampered);
    public static native boolean secureRequestVerification(boolean isTampered);
    
    public static native boolean secureValidateApiKey(String apiKey, boolean isTampered);
    public static native void secureTriggerDestruction(boolean isTampered);
    public static native void secureShutdown();
    
    public static native boolean secureAttemptHealing();
}