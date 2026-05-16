// src/main/java/com/katorymnd/pawapay/sdk/utils/vm/BytecodeInterpreter.java
package com.katorymnd.pawapay.sdk.utils.vm;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.katorymnd.pawapay.sdk.core.NativeCore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.*;

/**
 * VM Interpreter with Imprint-bound bytecode and shuffled opcodes.
 * Surgically bridged to Rust Native Core for Hardware-Arch binding.
 * Bytecode stored as plain JSON (matching Python SDK approach).
 */
public class BytecodeInterpreter {

    private static final Logger LOGGER = LoggerFactory.getLogger(BytecodeInterpreter.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    // =========================================================================
    // HONEYPOT STATE - Decoy traps for reverse engineers
    // =========================================================================
    public static boolean isVmDecrypted = false;
    public static String decoyKey = "KATORYMND_SECURE_8821"; 
    // =========================================================================

    private static final ImprintBoundVM vmLoader = new ImprintBoundVM();

    private final Stack<Object> stack;
    private int ip; 
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

    public int run() {
        int javaResult = executeDumLoop();

        try {
            String contextJson = mapper.writeValueAsString(this.context);
            return NativeCore.secureRunInterpreter(javaResult, contextJson);
        } catch (Exception e) {
            LOGGER.error("[PawaPay][VM] Failed to verify with Rust core: {}", e.getMessage());
            return 4; 
        }
    }

    private int executeDumLoop() {
        int originalIp = this.ip;
        try {
            this.ip = originalIp;
            while (ip < code.size()) {
                Instruction instr = code.get(ip);
                Map<Integer, String> opcodeMap = new HashMap<>();
                Map<String, Integer> currentOps = (opcodes == null || opcodes.isEmpty()) ? 
                    vmLoader.getDefaultOpcodes() : opcodes;
                
                for (Map.Entry<String, Integer> entry : currentOps.entrySet()) {
                    opcodeMap.put(entry.getValue(), entry.getKey());
                }

                String opcodeName = opcodeMap.getOrDefault(instr.op, "UNKNOWN");

                switch (opcodeName) {
                    case "PUSH_CONST":
                        stack.push(instr.arg != null ? instr.arg : 0);
                        break;
                         case "PUSH_STATE":
                        int arg = instr.arg != null ? instr.arg : 0;
                        if (arg == 0) {
                            // Safe handling - violations could be Integer or List
                            Object violationsObj = context.get("violations");
                            int violationCount = 0;
                            if (violationsObj instanceof List) {
                                violationCount = ((List<?>) violationsObj).size();
                            } else if (violationsObj instanceof Number) {
                                violationCount = ((Number) violationsObj).intValue();
                            }
                            stack.push(violationCount);
                        } else if (arg == 1) {
                            Object maxObj = context.get("max_violations");
                            if (maxObj instanceof Number) {
                                stack.push(((Number) maxObj).intValue());
                            } else {
                                stack.push(3);
                            }
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
        } finally {
            this.ip = originalIp;
            this.stack.clear();
        }
        return 0;
    }

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

    public static class Instruction {
        public int op;
        public Integer arg;
        public Instruction() {}
        public Instruction(int op, Integer arg) {
            this.op = op;
            this.arg = arg;
        }
    }

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
            
            String nativeHardwareAnchor = NativeCore.initializeHardwareVault();
            LOGGER.info("[PawaPay][VM] Hardware Anchor: {}", nativeHardwareAnchor);
            
            try {
                String fileImprint = "";
                if (Files.exists(imprintPath)) {
                    fileImprint = new String(Files.readAllBytes(imprintPath), StandardCharsets.UTF_8).trim();
                    LOGGER.info("[DEBUG] Imprint file exists. File imprint UUID: {}", fileImprint);
                } else {
                    fileImprint = "temp-" + UUID.randomUUID().toString();
                    LOGGER.info("[DEBUG] No imprint file. Generated temp: {}", fileImprint);
                }
                
                this.imprint = fileImprint;

                String projectPath = System.getProperty("user.dir");
                LOGGER.info("[DEBUG] Project path: {}", projectPath);
                
                String imprintHashInput = "IMPRINT-HASH:" + this.imprint + ":" + projectPath;
                this.imprintHash = hashSha256(imprintHashInput).substring(0, 16);

                LOGGER.info("[PawaPay][VM] Imprint hash initialized: {}", this.imprintHash);
                LOGGER.info("[DEBUG] Imprint hash input was: {}", imprintHashInput);

            } catch (Exception err) {
                LOGGER.error("[PawaPay][VM] Failed to initialize imprint: {}", err.getMessage());
                this.imprintHash = "error";
            }
        }

        public void loadBytecode() {
            if (this.bytecodeCache != null) return;

            if (decoyKey.equals("CRACKED")) {
                this.bytecodeCache = getSelfDestructBytecode();
                return;
            }

            try {
                InputStream is = BytecodeInterpreter.class.getClassLoader()
                    .getResourceAsStream("com/katorymnd/pawapay/sdk/utils/vm/bytecode.bin");
                
                if (is == null) is = BytecodeInterpreter.class.getClassLoader().getResourceAsStream("bytecode.bin");
                if (is == null) throw new IOException("Bytecode binary missing from resources.");
                
                JsonNode compiled = mapper.readTree(is);
                
                LOGGER.info("[DEBUG] bytecode.bin loaded. Has imprint field: {}", compiled.has("imprint"));
                if (compiled.has("imprint")) {
                    LOGGER.info("[DEBUG] bytecode.bin stored imprint: {}", compiled.get("imprint").asText());
                }
                LOGGER.info("[DEBUG] Our computed  imprintHash: {}", this.imprintHash);
                LOGGER.info("[DEBUG] Imprint match: {}", compiled.has("imprint") && compiled.get("imprint").asText().equals(this.imprintHash));

                if (compiled.has("imprint") && !compiled.get("imprint").asText().equals(this.imprintHash)) {
                    LOGGER.error("[PawaPay][VM] Bytecode not bound to this installation");
                    LOGGER.error("[DEBUG] Expected: {}", this.imprintHash);
                    LOGGER.error("[DEBUG] Got:      {}", compiled.get("imprint").asText());
                    throw new IllegalArgumentException("Invalid installation");
                }

                // ============================================================
                // PLAIN JSON APPROACH (matching Python SDK)
                // No AES decryption - content is stored as plain JSON
                // ============================================================
                if (compiled.has("data") && compiled.get("data").isObject()) {
                    JsonNode dataNode = compiled.get("data");
                    // Content is plain JSON string (matching Python SDK approach)
                    String content = dataNode.get("content").asText();
                    
                    LOGGER.info("[DEBUG] Content (first 80): {}...", content.length() > 80 ? content.substring(0, 80) : content);
                    LOGGER.info("[DEBUG] Content length: {}", content.length());
                    
                    // Verify checksum
                    String verifyChecksumInput = content + (compiled.has("imprint") ? compiled.get("imprint").asText() : "");
                    String checksum = hashSha256(verifyChecksumInput);
                    String expectedChecksum = compiled.path("checksum").asText();
                    
                    LOGGER.info("[DEBUG] Computed checksum:  {}", checksum);
                    LOGGER.info("[DEBUG] Expected checksum: {}", expectedChecksum);
                    LOGGER.info("[DEBUG] Checksum match: {}", checksum.equals(expectedChecksum));
                    
                    if (!checksum.equals(expectedChecksum)) {
                        LOGGER.error("[DEBUG] CHECKSUM MISMATCH!");
                        throw new IllegalArgumentException("Bytecode integrity compromised");
                    }
                    
                    this.bytecodeCache = mapper.readTree(content);
                    LOGGER.info("[DEBUG] Bytecode successfully loaded! Entry: {}", 
                        this.bytecodeCache.has("entry") ? this.bytecodeCache.get("entry").asInt() : "N/A");
                } else if (compiled.has("entry") && compiled.has("code")) {
                    // Direct bytecode (no data wrapper)
                    this.bytecodeCache = compiled;
                    LOGGER.info("[DEBUG] Direct bytecode loaded");
                } else {
                    this.bytecodeCache = getSelfDestructBytecode();
                    LOGGER.info("[DEBUG] No valid bytecode found, using self-destruct");
                }

                this.opcodes = loadShuffledOpcodes();

            } catch (Exception error) {
                LOGGER.error("[PawaPay][VM] Failed to load bytecode: {}", error.getMessage());
                this.bytecodeCache = getSelfDestructBytecode();
                this.opcodes = getDefaultOpcodes();
            }
        }

        private Map<String, Integer> loadShuffledOpcodes() {
            try {
                InputStream is = BytecodeInterpreter.class.getClassLoader()
                    .getResourceAsStream("com/katorymnd/pawapay/sdk/utils/vm/opcodes.json");
                
                if (is == null) is = BytecodeInterpreter.class.getClassLoader().getResourceAsStream("opcodes.json");
                
                if (is != null) {
                    JsonNode cached = mapper.readTree(is);
                    LOGGER.info("[DEBUG] opcodes.json loaded. Has imprintHash field: {}", cached.has("imprintHash"));
                    if (cached.has("imprintHash")) {
                        LOGGER.info("[DEBUG] opcodes.json stored imprintHash: {}", cached.get("imprintHash").asText());
                    }
                    LOGGER.info("[DEBUG] Our computed  imprintHash: {}", this.imprintHash);
                    
                    if (this.imprintHash.equals(cached.path("imprintHash").asText(null))) {
                        LOGGER.info("[DEBUG] Opcodes imprintHash MATCH!");
                        return mapper.convertValue(cached.get("opcodes"), new TypeReference<Map<String, Integer>>() {});
                    } else {
                        LOGGER.info("[DEBUG] Opcodes imprintHash MISMATCH - using defaults");
                    }
                }
                return getDefaultOpcodes();
            } catch (Exception error) {
                LOGGER.error("[DEBUG] Failed to load opcodes: {}", error.getMessage());
                return getDefaultOpcodes();
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
            ops.put("PUSH_CONST", 0x10); ops.put("PUSH_STATE", 0x11);
            ops.put("CMP_GT", 0x12); ops.put("CMP_EQ", 0x13);
            ops.put("AND", 0x14); ops.put("OR", 0x15);
            ops.put("NOT", 0x16); ops.put("JUMP_IF_FALSE", 0x17);
            ops.put("JUMP", 0x18); ops.put("RETURN", 0x19);
            return ops;
        }

        public String getOpcodeName(int opcodeValue) {
            if (this.opcodes != null) {
                for (Map.Entry<String, Integer> entry : this.opcodes.entrySet()) {
                    if (entry.getValue() == opcodeValue) return entry.getKey();
                }
            }
            return "UNKNOWN";
        }

        public Map<String, Integer> getOpcodes() { return opcodes; }
        public int getBytecodeCacheEntry() { return (bytecodeCache != null && bytecodeCache.has("entry")) ? bytecodeCache.get("entry").asInt(0) : 0; }
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

        private String hashSha256(String input) throws Exception {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        }
    }
}