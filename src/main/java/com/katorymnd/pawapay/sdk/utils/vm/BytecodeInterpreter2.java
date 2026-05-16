// src/main/java/com/katorymnd/pawapay/sdk/utils/vm/BytecodeInterpreter.java
package com.katorymnd.pawapay.sdk.utils.vm;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.*;

/**
 * VM Interpreter with Imprint-bound bytecode and shuffled opcodes for license decisions
 */
public class BytecodeInterpreter {

    private static final Logger LOGGER = LoggerFactory.getLogger(BytecodeInterpreter.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    // Global VM loader singleton instance equivalent
    private static final ImprintBoundVM vmLoader = new ImprintBoundVM();

    private final Stack<Object> stack;
    private int ip; // Instruction pointer
    private final Map<String, Object> context;
    private List<Instruction> code;
    private Map<String, Integer> opcodes;

    public BytecodeInterpreter(Map<String, Object> context) {
        this.stack = new Stack<>();
        this.ip = 0;
        this.context = context != null ? context : new HashMap<>();
        
        vmLoader.loadBytecode();
        
        this.code = vmLoader.getBytecodeCacheCode();
        this.ip = vmLoader.getBytecodeCacheEntry();
        this.opcodes = vmLoader.getOpcodes();
    }

    /**
     * Run the VM bytecode
     * @return Execution result
     */
    public int run() {
        while (ip < code.size()) {
            Instruction instr = code.get(ip);

            // Execute instruction based on shuffled opcodes
            Map<Integer, String> opcodeMap = new HashMap<>();
            if (opcodes == null || opcodes.isEmpty()) {
                Map<String, Integer> defaultOps = vmLoader.getDefaultOpcodes();
                for (Map.Entry<String, Integer> entry : defaultOps.entrySet()) {
                    opcodeMap.put(entry.getValue(), entry.getKey());
                }
            } else {
                for (Map.Entry<String, Integer> entry : opcodes.entrySet()) {
                    opcodeMap.put(entry.getValue(), entry.getKey());
                }
            }

            String opcodeName = opcodeMap.getOrDefault(instr.op, "UNKNOWN");

            switch (opcodeName) {
                case "PUSH_CONST":
                    stack.push(instr.arg != null ? instr.arg : 0);
                    break;

                case "PUSH_STATE":
                    int arg = instr.arg != null ? instr.arg : 0;
                    if (arg == 0) {
                        List<?> violations = (List<?>) context.getOrDefault("violations", new ArrayList<>());
                        stack.push(violations.size());
                    } else if (arg == 1) {
                        stack.push(context.getOrDefault("max_violations", 3));
                    } else {
                        stack.push(0);
                    }
                    break;

                case "CMP_GT":
                    if (stack.size() >= 2) {
                        int b = toInt(stack.pop());
                        int a = toInt(stack.pop());
                        stack.push(a > b);
                    }
                    break;

                case "CMP_EQ":
                    if (stack.size() >= 2) {
                        Object b = stack.pop();
                        Object a = stack.pop();
                        stack.push(Objects.equals(a, b));
                    }
                    break;

                case "AND":
                    if (stack.size() >= 2) {
                        boolean b = toBool(stack.pop());
                        boolean a = toBool(stack.pop());
                        stack.push(a && b);
                    }
                    break;

                case "OR":
                    if (stack.size() >= 2) {
                        boolean b = toBool(stack.pop());
                        boolean a = toBool(stack.pop());
                        stack.push(a || b);
                    }
                    break;

                case "NOT":
                    if (!stack.isEmpty()) {
                        boolean a = toBool(stack.pop());
                        stack.push(!a);
                    }
                    break;

                case "JUMP_IF_FALSE":
                    if (!stack.isEmpty()) {
                        boolean cond = toBool(stack.pop());
                        if (!cond) {
                            ip = instr.arg != null ? instr.arg : 0;
                            continue;
                        }
                    }
                    break;

                case "JUMP":
                    ip = instr.arg != null ? instr.arg : 0;
                    continue;

                case "RETURN":
                    if (!stack.isEmpty()) {
                        return toInt(stack.pop());
                    }
                    return 0;

                default:
                    LOGGER.warn("[PawaPay][VM] Unknown opcode: {}", opcodeName);
                    return 0;
            }
            ip++;
        }

        // If we exit without RETURN, return 0 (normal)
        return 0;
    }

    public void disassemble() {
        System.out.println("\n[PawaPay][VM] Bytecode Disassembly:");
        System.out.println("==================================================");
        System.out.println("Entry point: " + vmLoader.getBytecodeCacheEntry());
        System.out.println("Opcodes mapping:");

        Map<String, Integer> currentOpcodes = (opcodes != null && !opcodes.isEmpty()) ? opcodes : vmLoader.getDefaultOpcodes();
        for (Map.Entry<String, Integer> entry : currentOpcodes.entrySet()) {
            System.out.printf("  %s: 0x%x\n", entry.getKey(), entry.getValue());
        }

        System.out.println("\nInstructions:");
        for (int i = 0; i < code.size(); i++) {
            Instruction instr = code.get(i);
            String opcodeName = vmLoader.getOpcodeName(instr.op);
            String argStr = instr.arg != null ? "arg: " + instr.arg : "";
            System.out.printf("%3d: %-20s %s\n", i, opcodeName, argStr);
        }
        System.out.println("==================================================");
    }

    public void debugBytecode() {
        System.out.println("\n[PawaPay][VM] Bytecode Debug:");
        System.out.println("============================================================");
        System.out.println("Opcodes Mapping:");
        Map<String, Integer> currentOpcodes = (opcodes != null && !opcodes.isEmpty()) ? opcodes : vmLoader.getDefaultOpcodes();
        for (Map.Entry<String, Integer> entry : currentOpcodes.entrySet()) {
            System.out.printf("  %s: 0x%x\n", entry.getKey(), entry.getValue());
        }

        System.out.println("\nBytecode Instructions:");
        for (int i = 0; i < code.size(); i++) {
            Instruction instr = code.get(i);
            String opcodeName = vmLoader.getOpcodeName(instr.op);
            String argStr = instr.arg != null ? "arg: " + instr.arg : "";
            System.out.printf("%3d: %-20s %s\n", i, opcodeName, argStr);
        }

        System.out.println("\nContext: " + context);
        System.out.println("============================================================");
    }

    // Helper conversions for Python's duck-typing behavior in stack operations
    private int toInt(Object obj) {
        if (obj instanceof Number) return ((Number) obj).intValue();
        if (obj instanceof Boolean) return ((Boolean) obj) ? 1 : 0;
        return 0;
    }

    private boolean toBool(Object obj) {
        if (obj instanceof Boolean) return (Boolean) obj;
        if (obj instanceof Number) return ((Number) obj).intValue() != 0;
        return false;
    }

    /**
     * DTO for Instructions
     */
    public static class Instruction {
        public int op;
        public Integer arg;

        public Instruction() {}

        public Instruction(int op, Integer arg) {
            this.op = op;
            this.arg = arg;
        }
    }

    /**
     * VM loader with imprint binding encapsulated as a static internal class
     */
    private static class ImprintBoundVM {
        private JsonNode bytecodeCache;
        private Map<String, Integer> opcodes;
        private String imprint;
        private String imprintHash;

        public ImprintBoundVM() {
            initializeImprint();
        }

        private void initializeImprint() {
            Path imprintPath = Paths.get(System.getProperty("user.dir"), ".pawapay-imprint");
            try {
                if (Files.exists(imprintPath)) {
                    this.imprint = new String(Files.readAllBytes(imprintPath), StandardCharsets.UTF_8).trim();
                    LOGGER.info("[PawaPay][VM] Loaded imprint from: {}", imprintPath);
                    LOGGER.info("[PawaPay][VM] Imprint: {}...", this.imprint.substring(0, Math.min(8, this.imprint.length())));
                } else {
                    LOGGER.info("[PawaPay][VM] No imprint file found at: {}", imprintPath);
                    this.imprint = "temp-" + UUID.randomUUID().toString();
                    LOGGER.info("[PawaPay][VM] Generated temporary imprint: {}...", this.imprint.substring(0, Math.min(8, this.imprint.length())));
                }

                String projectPath = System.getProperty("user.dir");
                String imprintHashInput = "IMPRINT-HASH:" + this.imprint + ":" + projectPath;
                this.imprintHash = hashSha256(imprintHashInput).substring(0, 16);

                LOGGER.info("[PawaPay][VM] Imprint hash: {}", this.imprintHash);

            } catch (Exception err) {
                LOGGER.error("[PawaPay][VM] Failed to initialize imprint: {}", err.getMessage());
                this.imprint = "error-" + (System.currentTimeMillis() / 1000);
                this.imprintHash = "error";
            }
        }

        private String getOrCreateImprint() {
            if (this.imprint != null &&
                !this.imprint.startsWith("temp-") &&
                !this.imprint.startsWith("error-") &&
                !this.imprint.startsWith("mem-")) {
                return this.imprint;
            }

            Path imprintPath = Paths.get(System.getProperty("user.dir"), ".pawapay-imprint");
            try {
                if (Files.exists(imprintPath)) {
                    this.imprint = new String(Files.readAllBytes(imprintPath), StandardCharsets.UTF_8).trim();
                    return this.imprint;
                }
            } catch (Exception ignored) { }

            return this.imprint != null ? this.imprint : "unknown-imprint";
        }

        private String getMinimalHardwareHint() {
            try {
                List<String> hints = new ArrayList<>();
                String hostname = InetAddress.getLocalHost().getHostName();
                hints.add("h:" + (hostname.length() > 8 ? hostname.substring(0, 8) : hostname));
                
                String arch = System.getProperty("os.arch");
                hints.add("a:" + (arch.length() > 2 ? arch.substring(0, 2) : arch));

                return String.join(",", hints);
            } catch (Exception e) {
                return "hw-unknown";
            }
        }

        public void loadBytecode() {
            if (this.bytecodeCache != null) return;

            try (InputStream is = BytecodeInterpreter.class.getClassLoader().getResourceAsStream("bytecode.bin")) {
                if (is == null) {
                    throw new IOException("Bytecode file not found in JAR resources");
                }
                
                JsonNode compiled = mapper.readTree(is);

                if (compiled.has("imprint") && !compiled.get("imprint").asText().equals(this.imprintHash)) {
                    LOGGER.error("[PawaPay][VM] Bytecode not bound to this installation");
                    throw new IllegalArgumentException("Invalid installation");
                }

                String decryptionKey = generateDecryptionKey();
                LOGGER.info("[PawaPay][VM] Generated decryption key (first 16 chars): {}...", decryptionKey.substring(0, 16));

                // Placeholder for decryption logic
                if (compiled.has("data") && compiled.get("data").has("content")) {
                    String decrypted = mapper.writeValueAsString(compiled.get("data"));
                    
                    String verifyChecksumInput = decrypted + (compiled.has("imprint") ? compiled.get("imprint").asText() : "");
                    String verifyChecksum = hashSha256(verifyChecksumInput);

                    if (!verifyChecksum.equals(compiled.path("checksum").asText(null))) {
                        throw new IllegalArgumentException("Bytecode tampering detected");
                    }
                    this.bytecodeCache = mapper.readTree(decrypted);
                } else {
                    this.bytecodeCache = getSelfDestructBytecode();
                }

                this.opcodes = loadShuffledOpcodes();

            } catch (Exception error) {
                LOGGER.error("[PawaPay][VM] Failed to load bytecode: {}", error.getMessage());
                this.bytecodeCache = getSelfDestructBytecode();
                this.opcodes = getDefaultOpcodes();
            }
        }

        private Map<String, Integer> loadShuffledOpcodes() {
            try (InputStream is = BytecodeInterpreter.class.getClassLoader().getResourceAsStream("opcodes.json")) {
                if (is != null) {
                    JsonNode cached = mapper.readTree(is);

                    if (this.imprintHash.equals(cached.path("imprintHash").asText(null))) {
                        LOGGER.info("[PawaPay][VM] Loaded cached shuffled opcodes");
                        return mapper.convertValue(cached.get("opcodes"), new TypeReference<Map<String, Integer>>() {});
                    }
                }

                LOGGER.info("[PawaPay][VM] Generating new shuffled opcodes for this installation");
                // Using defaults as a graceful fallback in Java environment missing the generator.
                return getDefaultOpcodes();

            } catch (Exception error) {
                LOGGER.error("[PawaPay][VM] Failed to load/generate shuffled opcodes, using defaults: {}", error.getMessage());
                return getDefaultOpcodes();
            }
        }

        private String generateDecryptionKey() {
            try {
                String imp = getOrCreateImprint();
                
                byte[] derived = hashSha512Bytes("IMPRINT-CORE:" + imp);
                String projectPath = System.getProperty("user.dir");
                
                derived = hashSha512Bytes(concatBytes(derived, ("PATH-LOCK:" + projectPath).getBytes(StandardCharsets.UTF_8)));
                
                String hardwareHint = getMinimalHardwareHint();
                derived = hashSha512Bytes(concatBytes(derived, ("HW-ANCHOR:" + hardwareHint).getBytes(StandardCharsets.UTF_8)));
                
                return bytesToHex(derived).substring(0, 64); // 32 bytes = 64 hex chars
            } catch (Exception e) {
                return "fallback-key";
            }
        }

        private JsonNode getSelfDestructBytecode() {
            Map<String, Integer> defaultOps = getDefaultOpcodes();
            try {
                String jsonStr = String.format(
                    "{\"entry\": 0, \"code\": [{\"op\": %d, \"arg\": 2}, {\"op\": %d}]}",
                    defaultOps.get("PUSH_CONST"), defaultOps.get("RETURN")
                );
                return mapper.readTree(jsonStr);
            } catch (Exception e) {
                return mapper.createObjectNode();
            }
        }

        public Map<String, Integer> getDefaultOpcodes() {
            Map<String, Integer> ops = new HashMap<>();
            ops.put("PUSH_CONST", 0x10);
            ops.put("PUSH_STATE", 0x11);
            ops.put("CMP_GT", 0x12);
            ops.put("CMP_EQ", 0x13);
            ops.put("AND", 0x14);
            ops.put("OR", 0x15);
            ops.put("NOT", 0x16);
            ops.put("JUMP_IF_FALSE", 0x17);
            ops.put("JUMP", 0x18);
            ops.put("RETURN", 0x19);
            return ops;
        }

        public String getOpcodeName(int opcodeValue) {
            if (this.opcodes != null) {
                for (Map.Entry<String, Integer> entry : this.opcodes.entrySet()) {
                    if (entry.getValue() == opcodeValue) return entry.getKey();
                }
            }

            Map<Integer, String> originalOpcodes = new HashMap<>();
            originalOpcodes.put(0x01, "PUSH_CONST");
            originalOpcodes.put(0x02, "PUSH_STATE");
            originalOpcodes.put(0x03, "CMP_GT");
            originalOpcodes.put(0x04, "CMP_EQ");
            originalOpcodes.put(0x05, "AND");
            originalOpcodes.put(0x06, "OR");
            originalOpcodes.put(0x07, "NOT");
            originalOpcodes.put(0x08, "JUMP_IF_FALSE");
            originalOpcodes.put(0x09, "JUMP");
            originalOpcodes.put(0x0A, "RETURN");

            if (originalOpcodes.containsKey(opcodeValue)) {
                return originalOpcodes.get(opcodeValue) + " (original)";
            }

            return String.format("UNKNOWN_0x%x", opcodeValue);
        }

        // --- Getters for the outer class ---
        public Map<String, Integer> getOpcodes() { return opcodes; }
        
        public int getBytecodeCacheEntry() {
            return (bytecodeCache != null && bytecodeCache.has("entry")) ? bytecodeCache.get("entry").asInt(0) : 0;
        }

        public List<Instruction> getBytecodeCacheCode() {
            List<Instruction> instructions = new ArrayList<>();
            if (bytecodeCache != null && bytecodeCache.has("code")) {
                for (JsonNode node : bytecodeCache.get("code")) {
                    Integer arg = node.has("arg") ? node.get("arg").asInt() : null;
                    instructions.add(new Instruction(node.get("op").asInt(), arg));
                }
            }
            return instructions;
        }

        // --- Cryptography Helpers ---
        private String hashSha256(String input) throws Exception {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        }

        private byte[] hashSha512Bytes(String input) throws Exception {
            return hashSha512Bytes(input.getBytes(StandardCharsets.UTF_8));
        }

        private byte[] hashSha512Bytes(byte[] input) throws Exception {
            MessageDigest digest = MessageDigest.getInstance("SHA-512");
            return digest.digest(input);
        }

        private byte[] concatBytes(byte[] a, byte[] b) {
            byte[] result = new byte[a.length + b.length];
            System.arraycopy(a, 0, result, 0, a.length);
            System.arraycopy(b, 0, result, a.length, b.length);
            return result;
        }

        private String bytesToHex(byte[] bytes) {
            StringBuilder hexString = new StringBuilder();
            for (byte b : bytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        }
    }
}