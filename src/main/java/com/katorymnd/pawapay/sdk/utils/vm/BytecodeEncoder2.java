// src/main/java/com/katorymnd/pawapay/sdk/utils/vm/BytecodeEncoder.java
package com.katorymnd.pawapay.sdk.utils.vm;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;

/**
 * Bytecode Encoder/Decoder
 * Uses the superior PawaPay Imprint system for key generation
 * Imprint is the SOUL - everything revolves around it
 */
public class BytecodeEncoder {

    private static final Logger LOGGER = LoggerFactory.getLogger(BytecodeEncoder.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private final Path imprintPath;
    private final String secretKey;

    public BytecodeEncoder(String secretKey) {
        // Set imprint path FIRST before generating key
        this.imprintPath = Paths.get(System.getProperty("user.dir"), ".pawapay-imprint");
        this.secretKey = secretKey;
    }

    public BytecodeEncoder() {
        this(null);
    }

    private String getKey() {
        if (this.secretKey != null && !this.secretKey.isEmpty()) {
            return this.secretKey;
        }
        return generateImprintBasedKey();
    }

    public boolean generateClientFiles(String outputDir) {
        try {
            LOGGER.info("[PawaPay][VM] Initializing client-side protection...");

            // A. Force Imprint Creation (The "Soul")
            String imprint = getOrCreateImprint();
            LOGGER.info("[PawaPay][VM] Bound to Imprint: {}...", imprint.substring(0, Math.min(8, imprint.length())));

            // B. Generate unique Opcode Shuffle for this machine
            Map<String, Integer> shuffledOpcodes = generateShuffledOpcodes();

            // C. Save Opcodes
            Path outputPath = Paths.get(outputDir);
            if (!Files.exists(outputPath)) {
                Files.createDirectories(outputPath);
            }

            Path opcodesPath = outputPath.resolve("opcodes.json");
            saveShuffledOpcodes(opcodesPath.toString(), shuffledOpcodes);

            // D. Get the Internal Logic
            Map<String, Object> sourceLogic = getInternalLogic(shuffledOpcodes);

            // E. Compile & Encrypt for this specific machine
            Path bytecodePath = outputPath.resolve("bytecode.bin");
            writeEncrypted(bytecodePath.toString(), sourceLogic);

            LOGGER.info("[PawaPay][VM] Client protection successfully imprinted.");
            return true;

        } catch (Exception err) {
            LOGGER.error("[PawaPay][VM] Generation failed: {}", err.getMessage(), err);
            return false;
        }
    }

    private Map<String, Object> getInternalLogic(Map<String, Integer> ops) {
        Map<String, Object> logic = new HashMap<>();
        logic.put("entry", 0);

        List<Map<String, Object>> code = new ArrayList<>();

        // Logic: if (violations >= maxViolations) -> DESTROY(2)
        code.add(createInstr(ops.getOrDefault("PUSH_STATE", 0x02), 0));     // Push violations
        code.add(createInstr(ops.getOrDefault("PUSH_STATE", 0x02), 1));     // Push max
        code.add(createInstr(ops.getOrDefault("CMP_GT", 0x03), null));      // >
        code.add(createInstr(ops.getOrDefault("PUSH_STATE", 0x02), 0));     // Push violations
        code.add(createInstr(ops.getOrDefault("PUSH_STATE", 0x02), 1));     // Push max
        code.add(createInstr(ops.getOrDefault("CMP_EQ", 0x04), null));      // ==
        code.add(createInstr(ops.getOrDefault("OR", 0x06), null));          // >=
        code.add(createInstr(ops.getOrDefault("JUMP_IF_FALSE", 0x08), 12)); // Safe? Jump to end
        code.add(createInstr(ops.getOrDefault("PUSH_CONST", 0x01), 2));     // DESTROY
        code.add(createInstr(ops.getOrDefault("RETURN", 0x0A), null));
        code.add(createInstr(ops.getOrDefault("PUSH_CONST", 0x01), 0));     // OK
        code.add(createInstr(ops.getOrDefault("RETURN", 0x0A), null));

        logic.put("code", code);
        return logic;
    }

    private Map<String, Object> createInstr(int op, Integer arg) {
        Map<String, Object> instr = new HashMap<>();
        instr.put("op", op);
        if (arg != null) {
            instr.put("arg", arg);
        }
        return instr;
    }

    private String getOrCreateImprint() {
        try {
            if (Files.exists(imprintPath)) {
                String existingImprint = new String(Files.readAllBytes(imprintPath), StandardCharsets.UTF_8).trim();
                if (!existingImprint.isEmpty()) {
                    return existingImprint;
                }
            }

            // Generate new "Soul" if missing
            LOGGER.info("[PawaPay][VM] Creating new imprint at: {}", imprintPath);
            String newImprint = UUID.randomUUID().toString();

            try {
                Files.write(imprintPath, newImprint.getBytes(StandardCharsets.UTF_8));
                // Try to set restricted permissions (Unix-like chmod 600)
                try {
                    Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rw-------");
                    Files.setPosixFilePermissions(imprintPath, perms);
                } catch (UnsupportedOperationException ignored) {
                    // Windows systems will throw this, safely ignore.
                }
                LOGGER.info("[PawaPay][VM] Created new VM bytecode imprint");
            } catch (Exception writeErr) {
                LOGGER.warn("[PawaPay][VM] Write restricted failed: {}", writeErr.getMessage());
                Files.write(imprintPath, newImprint.getBytes(StandardCharsets.UTF_8));
            }

            return newImprint;

        } catch (Exception err) {
            LOGGER.error("[PawaPay][VM] Could not access imprint file, using in-memory: {}", err.getMessage());
            return "mem-" + UUID.randomUUID();
        }
    }

    public String generateImprintBasedKey() {
        try {
            String imprint = getOrCreateImprint();
            return deriveKeyFromImprint(imprint);
        } catch (Exception error) {
            LOGGER.error("[PawaPay][VM] Key gen failed: {}", error.getMessage());
            return generateFallbackKey();
        }
    }

    private String deriveKeyFromImprint(String imprint) throws Exception {
        // Round 1: Imprint Hash
        byte[] derived = hashSha512Bytes(("IMPRINT-CORE:" + imprint).getBytes(StandardCharsets.UTF_8));

        // Round 2: Path Locking
        String projectPath = System.getProperty("user.dir");
        derived = hashSha512Bytes(concatBytes(derived, ("PATH-LOCK:" + projectPath).getBytes(StandardCharsets.UTF_8)));

        // Round 3: Hardware Anchor
        String hardwareHint = getMinimalHardwareHint();
        derived = hashSha512Bytes(concatBytes(derived, ("HW-ANCHOR:" + hardwareHint).getBytes(StandardCharsets.UTF_8)));

        // Extract 32 bytes for AES-256 and return as hex string
        return bytesToHex(Arrays.copyOfRange(derived, 0, 32));
    }

    private String getMinimalHardwareHint() {
        try {
            String hostname = InetAddress.getLocalHost().getHostName();
            String hostSnippet = hostname.length() > 8 ? hostname.substring(0, 8) : hostname;
            
            String arch = System.getProperty("os.arch");
            String archSnippet = arch.length() > 2 ? arch.substring(0, 2) : arch;
            
            return String.format("h:%s,a:%s", hostSnippet, archSnippet);
        } catch (Exception e) {
            return "hw-unknown";
        }
    }

    private String generateFallbackKey() {
        try {
            String fallbackRaw = String.format("fallback:%s:%d", System.getProperty("user.dir"), System.currentTimeMillis() / 1000);
            return hashSha256(fallbackRaw).substring(0, 64); // 32 bytes = 64 hex chars
        } catch (Exception e) {
            return "0000000000000000000000000000000000000000000000000000000000000000";
        }
    }

    public Map<String, Object> encrypt(String text) throws Exception {
        String key = getKey();
        byte[] keyBytes = hexToBytes(key);

        // Generate IV
        byte[] iv = new byte[16];
        new SecureRandom().nextBytes(iv);

        // Encrypt (Java's PKCS5Padding is equivalent to Python's PKCS7 for AES)
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(iv);

        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
        byte[] encrypted = cipher.doFinal(text.getBytes(StandardCharsets.UTF_8));

        Map<String, Object> result = new HashMap<>();
        result.put("iv", bytesToHex(iv));
        result.put("content", bytesToHex(encrypted));
        result.put("imprint", getImprintHash());

        return result;
    }

    public String decrypt(Map<String, Object> encryptedData) throws Exception {
        // Imprint verification
        String imprintValue = (String) encryptedData.get("imprint");
        if (imprintValue != null && !imprintValue.equals(getImprintHash())) {
            throw new IllegalArgumentException("VM bytecode is not valid for this installation (Imprint Mismatch)");
        }

        String key = getKey();
        byte[] keyBytes = hexToBytes(key);
        byte[] iv = hexToBytes((String) encryptedData.get("iv"));
        byte[] content = hexToBytes((String) encryptedData.get("content"));

        // Decrypt
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(iv);

        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
        byte[] decrypted = cipher.doFinal(content);

        return new String(decrypted, StandardCharsets.UTF_8);
    }

    private String getImprintHash() {
        try {
            String imprint = getOrCreateImprint();
            String projectPath = System.getProperty("user.dir");
            return hashSha256("IMPRINT-HASH:" + imprint + ":" + projectPath).substring(0, 16);
        } catch (Exception e) {
            return "error-hash";
        }
    }

    public Map<String, Object> compile(Map<String, Object> bytecodeJson) throws Exception {
        String jsonStr = mapper.writeValueAsString(bytecodeJson);
        String checksumInput = jsonStr + getImprintHash();
        String checksum = hashSha256(checksumInput);

        Map<String, Object> compiled = new HashMap<>();
        compiled.put("checksum", checksum);
        compiled.put("timestamp", System.currentTimeMillis() / 1000);
        compiled.put("version", "2.0");
        compiled.put("imprint", getImprintHash());
        compiled.put("data", encrypt(jsonStr));

        return compiled;
    }

    public void writeEncrypted(String outputPath, Map<String, Object> bytecodeJson) throws Exception {
        Map<String, Object> compiled = compile(bytecodeJson);
        Files.write(Paths.get(outputPath), mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(compiled));
        LOGGER.info("[PawaPay][VM] Encrypted bytecode written to {}", outputPath);
    }

    public Map<String, Object> readEncrypted(String inputPath) throws Exception {
        String raw = new String(Files.readAllBytes(Paths.get(inputPath)), StandardCharsets.UTF_8);
        Map<String, Object> compiled = mapper.readValue(raw, new TypeReference<Map<String, Object>>() {});

        // Decrypt (internally checks imprint)
        @SuppressWarnings("unchecked")
        Map<String, Object> dataMap = (Map<String, Object>) compiled.get("data");
        String decrypted = decrypt(dataMap);

        // Verify Checksum
        String imprintVal = compiled.containsKey("imprint") ? (String) compiled.get("imprint") : "";
        String verifyChecksumInput = decrypted + imprintVal;
        String verifyChecksum = hashSha256(verifyChecksumInput);

        if (!verifyChecksum.equals(compiled.get("checksum"))) {
            throw new IllegalArgumentException("VM bytecode integrity check failed");
        }

        return mapper.readValue(decrypted, new TypeReference<Map<String, Object>>() {});
    }

    public Map<String, Integer> generateShuffledOpcodes() {
        Map<String, Integer> baseOpcodes = new LinkedHashMap<>();
        baseOpcodes.put("PUSH_CONST", 0x01);
        baseOpcodes.put("PUSH_STATE", 0x02);
        baseOpcodes.put("CMP_GT", 0x03);
        baseOpcodes.put("CMP_EQ", 0x04);
        baseOpcodes.put("AND", 0x05);
        baseOpcodes.put("OR", 0x06);
        baseOpcodes.put("NOT", 0x07);
        baseOpcodes.put("JUMP_IF_FALSE", 0x08);
        baseOpcodes.put("JUMP", 0x09);
        baseOpcodes.put("RETURN", 0x0A);

        String imprint = getOrCreateImprint();
        LOGGER.info("[PawaPay][VM] Shuffling opcodes based on imprint: {}...", imprint.substring(0, Math.min(8, imprint.length())));

        try {
            // Create deterministic shuffle seed
            String hashMd5 = hashMd5("SHUFFLE:" + imprint);
            long shuffleSeed = Long.parseLong(hashMd5.substring(0, 8), 16);

            List<Map.Entry<String, Integer>> opcodeList = new ArrayList<>(baseOpcodes.entrySet());

            // Deterministic Shuffle (Fisher-Yates style mapping to Python implementation)
            for (int i = opcodeList.size() - 1; i > 0; i--) {
                int j = (int) ((shuffleSeed + i) % (i + 1));
                Map.Entry<String, Integer> temp = opcodeList.get(i);
                opcodeList.set(i, opcodeList.get(j));
                opcodeList.set(j, temp);
            }

            Map<String, Integer> shuffled = new LinkedHashMap<>();
            int nextCode = 0x10;
            for (Map.Entry<String, Integer> entry : opcodeList) {
                shuffled.put(entry.getKey(), nextCode);
                nextCode += 1;
            }

            return shuffled;
        } catch (Exception e) {
            LOGGER.error("Failed to shuffle opcodes, returning base", e);
            return baseOpcodes;
        }
    }

    public boolean saveShuffledOpcodes(String outputPath, Map<String, Integer> shuffledOpcodes) {
        try {
            Map<String, Object> cacheData = new HashMap<>();
            cacheData.put("imprintHash", getImprintHash());
            cacheData.put("generatedAt", System.currentTimeMillis() / 1000);
            cacheData.put("opcodes", shuffledOpcodes);

            Path path = Paths.get(outputPath);
            Files.write(path, mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(cacheData));

            // Set permissions
            try {
                Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rw-------");
                Files.setPosixFilePermissions(path, perms);
            } catch (UnsupportedOperationException ignored) {
                // Ignore on Windows
            }

            LOGGER.info("[PawaPay][VM] Saved shuffled opcodes to {}", outputPath);
            return true;

        } catch (Exception error) {
            LOGGER.error("[PawaPay][VM] Could not save opcodes: {}", error.getMessage());
            return false;
        }
    }

    // --- Cryptography Helpers ---

    private byte[] hashSha512Bytes(byte[] input) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-512");
        return digest.digest(input);
    }

    private String hashSha256(String input) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(hash);
    }

    private String hashMd5(String input) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("MD5");
        byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(hash);
    }

    private byte[] concatBytes(byte[] a, byte[] b) {
        byte[] result = new byte[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}