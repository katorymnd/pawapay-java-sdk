package com.katorymnd.pawapay.sdk.core;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class NativeCore {

    static {
        try {
            // First, try loading it from the standard Java library path (good for local development)
            System.loadLibrary("katorymnd_pawapay_core");
        } catch (UnsatisfiedLinkError e) {
            // If that fails, it means we are running from the compiled JAR.
            // We need to extract the DLL from the JAR to a temp folder and load it.
            try {
                loadLibraryFromJar("/native/katorymnd_pawapay_core.dll");
            } catch (Exception ex) {
                System.err.println("[PawaPay SDK] CRITICAL: Security core missing or tampered. Halting.");
                System.exit(1);
            }
        }
    }

    /**
     * Extracts a library packaged in the JAR to a temporary directory and loads it.
     */
    private static void loadLibraryFromJar(String path) throws Exception {
        InputStream in = NativeCore.class.getResourceAsStream(path);
        if (in == null) {
            throw new RuntimeException("Library " + path + " not found in JAR.");
        }

        // Create a temporary file to hold the extracted DLL
        File tempFile = File.createTempFile("pawapay_core_", ".dll");
        tempFile.deleteOnExit(); // Clean up when the JVM shuts down

        // Copy the DLL from the JAR to the temp file
        Files.copy(in, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        in.close();

        // Load the library using the absolute path of the temp file
        System.load(tempFile.getAbsolutePath());
    }

    // Native Hooks
    public static native int secureVaultInit(String domain, String secret);
    
    // Note how we pass the java flag into Rust so Rust can check for tampering
    public static native int getVaultDegradationLevel(boolean javaHoneypotFlag);

    public static native String initializeHardwareVault();
    public static native int secureRunInterpreter(int javaResult, String contextJson);

    public static native String deriveSecureKey(String imprint, String projectPath);
    
    // FIXED: Added missing key parameter to match Rust implementation
    public static native String secureEncrypt(String plaintext, String secureKeyHex, boolean isTampered);
    
    // FIXED: Added missing iv and key parameters to match Rust implementation
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
    
    // NEW: Added missing native methods
    public static native boolean secureValidateApiKey(String apiKey, boolean isTampered);
    public static native void secureTriggerDestruction(boolean isTampered);
    public static native void secureShutdown();
    
    // SELF-HEALING: Attempt to heal expired or forgiven violations
    public static native boolean secureAttemptHealing();
}