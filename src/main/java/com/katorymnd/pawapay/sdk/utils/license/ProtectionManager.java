// src/main/java/com/katorymnd/pawapay/sdk/utils/license/ProtectionManager.java
package com.katorymnd.pawapay.sdk.utils.license;

import com.katorymnd.pawapay.sdk.core.NativeCore;
import com.katorymnd.pawapay.sdk.utils.vm.BytecodeInterpreter;
import com.katorymnd.pawapay.sdk.utils.vm.DegradationManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Protection Layer
 * Self-destruct mechanism for license violations with VM-driven degradation
 */
public class ProtectionManager implements DegradationManager.ProtectionLayer {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProtectionManager.class);

    // Singleton instance
    private static final ProtectionManager INSTANCE = new ProtectionManager();

    // =========================================================================
    // HONEYPOT STATE - The Ultimate Decoys
    // Attackers will target these variables or the 'destroyed' boolean below.
    // =========================================================================
    public static boolean ignoreViolations = false;
    public static boolean preventDestruction = false;
    // =========================================================================

    // Core state (Decoy Layer)
    private boolean destroyed = false;
    private final List<Map<String, Object>> violations = Collections.synchronizedList(new ArrayList<>());
    private final int maxViolations = 3;

    // Success-based recovery configuration
    private int successThreshold = 5;
    private int consecutiveSuccesses = 0;
    private boolean nonDestructiveMode = false;
    private long violationExpirySeconds = 0; 

    // Time-based state
    private final long initializedAt;
    private long lastHealthCheck;
    private long lastViolationTime;
    private long lastNormalUseTime;
    private final long decayThreshold = 7 * 24 * 60 * 60; 
    private final int randomSeed;

    private ScheduledExecutorService decayMonitorService;

    private ProtectionManager() {
        long now = System.currentTimeMillis() / 1000;
        this.initializedAt = now;
        this.lastHealthCheck = now;
        this.lastViolationTime = now;
        this.lastNormalUseTime = now;
        this.randomSeed = new Random().nextInt(1001); 

        // --- SELF-HEALING: Attempt to heal expired violations on startup ---
        attemptNativeHealing();
        
        startDecayMonitor();
    }

    public static ProtectionManager getInstance() {
        return INSTANCE;
    }

    /**
     * Attempts self-healing via the Rust native core.
     * Can heal violations that have expired (7 days) or been forgiven by success count.
     */
    private void attemptNativeHealing() {
        try {
            boolean healed = NativeCore.secureAttemptHealing();
            if (healed) {
                LOGGER.info("[PawaPay] Native core self-healing successful - violations cleared or reduced");
                // Sync decoy state with healed Rust state
                boolean tampered = ignoreViolations || preventDestruction;
                int remainingViolations = NativeCore.secureGetViolationCount(violations.size(), tampered);
                if (remainingViolations == 0) {
                    violations.clear();
                    this.destroyed = false;
                }
            }
        } catch (Exception e) {
            LOGGER.debug("[PawaPay] Self-healing check skipped: {}", e.getMessage());
        }
    }

    private void startDecayMonitor() {
        decayMonitorService = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "PawaPay-DecayMonitor");
            t.setDaemon(true);
            return t;
        });
        decayMonitorService.scheduleAtFixedRate(this::checkAndApplyDecay, 1, 1, TimeUnit.HOURS);
    }

    private synchronized void checkAndApplyDecay() {
        if (isDestroyed()) {
            // Even if destroyed, try healing (violations may have expired)
            attemptNativeHealing();
            if (!isDestroyed()) {
                LOGGER.info("[PawaPay] SDK self-healed during decay check - operations restored");
            }
            return;
        }

        long now = System.currentTimeMillis() / 1000;
        long timeSinceNormalUse = now - lastNormalUseTime;
        long timeSinceLastViolation = now - lastViolationTime;

        performHealthCheck();

        if (!violations.isEmpty() && timeSinceNormalUse >= decayThreshold) {
            violations.remove(0);
            lastNormalUseTime = now;
        }

        if (timeSinceLastViolation > (30L * 24 * 60 * 60)) { 
            if (!violations.isEmpty()) {
                resetViolations();
            }
        }
        
        // Periodic healing attempt
        attemptNativeHealing();
    }

    private synchronized void pruneExpiredViolations() {
        if (violationExpirySeconds == 0 || violations.isEmpty()) return;
        long now = System.currentTimeMillis() / 1000;
        violations.removeIf(v -> (now - ((Number) v.get("timestamp")).longValue()) > violationExpirySeconds);
    }

    private boolean shouldDestroyViaVm() {
        try {
            long now = System.currentTimeMillis() / 1000;
            Map<String, Object> vmContext = new HashMap<>();
            
            // Pass the TRUE violation count to the VM, not the decoy
            vmContext.put("violations", getViolationCount());
            vmContext.put("max_violations", maxViolations);
            vmContext.put("non_destructive", nonDestructiveMode);
            vmContext.put("uptime", now - initializedAt);
            vmContext.put("random_seed", randomSeed);
            vmContext.put("consecutive_successes", consecutiveSuccesses);
            vmContext.put("time_since_last_violation", now - lastViolationTime);
            vmContext.put("time_since_normal_use", now - lastNormalUseTime);
            vmContext.put("hour_of_day", Calendar.getInstance().get(Calendar.HOUR_OF_DAY));

            BytecodeInterpreter vm = new BytecodeInterpreter(vmContext);
            int decision = vm.run();

            DegradationManager degradationManager = new DegradationManager(this);
            degradationManager.handleVmDegradationDecision(decision);

            return decision == 2; 

        } catch (Exception err) {
            LOGGER.error("[PawaPay][VM] VM execution failed: {}", err.getMessage());
            return false;
        }
    }

    public synchronized void recordViolation(String reason, Map<String, Object> options) {
        if (options == null) options = new HashMap<>();
        boolean silent = Boolean.TRUE.equals(options.get("silent"));

        if (!silent) {
            LOGGER.warn("[PawaPay License Violation] {}", reason);
        }

        pruneExpiredViolations();

        long now = System.currentTimeMillis() / 1000;
        Map<String, Object> violation = new HashMap<>();
        violation.put("reason", reason);
        violation.put("timestamp", now);
        violation.put("silent", silent);
        
        // Update decoy
        violations.add(violation);
        lastViolationTime = now;
        lastNormalUseTime = now;
        consecutiveSuccesses = 0;

        // --- SURGICAL STRIKE: Update the TRUE state in Rust ---
        boolean tampered = ignoreViolations || preventDestruction;
        NativeCore.secureRecordViolation(violations.size(), tampered);

        if (shouldDestroyViaVm()) {
            Map<String, Object> destroyOptions = new HashMap<>();
            destroyOptions.put("silent", silent);
            destroy(destroyOptions);
        }
    }

    public void recordViolation(String reason) {
        recordViolation(reason, null);
    }

    public synchronized void recordSuccess() {
        if (isDestroyed()) {
            // Try healing even when destroyed - successes might forgive violations
            attemptNativeHealing();
            if (!isDestroyed()) {
                LOGGER.info("[PawaPay] SDK self-healed after successful requests");
            } else {
                return;
            }
        }

        pruneExpiredViolations();
        lastNormalUseTime = System.currentTimeMillis() / 1000;

        // --- SURGICAL STRIKE: Record success in native memory ---
        boolean tampered = ignoreViolations || preventDestruction;
        NativeCore.secureRecordSuccess(consecutiveSuccesses, tampered);

        // Decoy visual behavior
        if (violations.isEmpty()) {
            consecutiveSuccesses = 0;
            return;
        }

        consecutiveSuccesses++;

        if (consecutiveSuccesses >= successThreshold) {
            if (!violations.isEmpty()) {
                violations.remove(0);
                consecutiveSuccesses = 0;
            }
        }
    }

    public synchronized void destroy(Map<String, Object> options) {
        if (isDestroyed()) return;
        
        if (options == null) options = new HashMap<>();

        // Set decoy
        destroyed = true;

        // --- SURGICAL STRIKE: Tell Rust to destroy the SDK ---
        boolean tampered = preventDestruction || ignoreViolations;
        NativeCore.secureTriggerDestruction(tampered);

        if (!Boolean.TRUE.equals(options.get("silent"))) {
            System.out.println("╔════════════════════════════════════════════════════════╗");
            System.out.println("║  PawaPay SDK License Violation - SDK Disabled          ║");
            System.out.println("║  Contact: support@katorymnd.com                        ║");
            System.out.println("║  Purchase license: https://katorymnd.com               ║");
            System.out.println("╚════════════════════════════════════════════════════════╝");
        }
    }

    public void destroy() {
        destroy(null);
    }

    public boolean isDestroyed() {
        // --- SURGICAL STRIKE: Ask Rust for the true destruction state ---
        boolean tampered = preventDestruction || ignoreViolations;
        return NativeCore.secureIsDestroyed(this.destroyed, tampered);
    }

    // --- Interface Methods for DegradationManager.ProtectionLayer ---
    
    @Override
    public synchronized int getViolationCount() {
        pruneExpiredViolations();
        
        // --- SURGICAL STRIKE: Ask Rust for the true count ---
        boolean tampered = ignoreViolations || preventDestruction;
        return NativeCore.secureGetViolationCount(violations.size(), tampered);
    }

    @Override
    public int getMaxViolations() {
        return maxViolations;
    }

    @Override
    public void triggerDestruction() {
        destroy();
    }
    
    // ----------------------------------------------------------------

    public synchronized void resetViolations() {
        // Clear decoys
        violations.clear();
        consecutiveSuccesses = 0;
        long now = System.currentTimeMillis() / 1000;
        lastViolationTime = now;
        lastNormalUseTime = now;

        // Reset real state
        NativeCore.secureResetViolations();

        LOGGER.info("[PawaPay] Violations reset by resetViolations()");
    }

    public void setNonDestructiveMode(boolean flag) {
        this.nonDestructiveMode = flag;
    }

    public void setSuccessThreshold(int n) {
        this.successThreshold = Math.max(1, n);
    }

    public void setViolationExpiry(long seconds) {
        this.violationExpirySeconds = Math.max(0, seconds);
    }

    public synchronized void performHealthCheck() {
        long now = System.currentTimeMillis() / 1000;
        if ((now - lastHealthCheck) > 24 * 60 * 60) { 
            // Try native healing first
            attemptNativeHealing();
            
            if (!violations.isEmpty() && !isDestroyed()) {
                Iterator<Map<String, Object>> iterator = violations.iterator();
                while (iterator.hasNext()) {
                    Map<String, Object> v = iterator.next();
                    if ((now - ((Number) v.get("timestamp")).longValue()) > 7 * 24 * 60 * 60) { 
                        iterator.remove();
                        // Also notify Rust to forgive a violation securely
                        NativeCore.secureRecordSuccess(5, false);
                        break;
                    }
                }
            }
        }
        lastHealthCheck = now;
    }

    public <T> CompletableFuture<T> callWithDegradation(String callId, Callable<CompletableFuture<T>> apiCall) {
        if (isDestroyed()) {
            // Try healing before failing
            attemptNativeHealing();
            if (isDestroyed()) {
                CompletableFuture<T> future = new CompletableFuture<>();
                future.completeExceptionally(new Exception("SDK is disabled due to license violations"));
                return future;
            }
        }

        try {
            DegradationManager dm = new DegradationManager(this);
            return dm.applyDegradation(callId, apiCall);
        } catch (Exception e) {
            CompletableFuture<T> future = new CompletableFuture<>();
            future.completeExceptionally(e);
            return future;
        }
    }

    public synchronized void shutdown() {
        if (decayMonitorService != null && !decayMonitorService.isShutdown()) {
            LOGGER.info("[PawaPay] Shutting down protection decay monitor...");
            decayMonitorService.shutdownNow();
            try {
                if (!decayMonitorService.awaitTermination(2, TimeUnit.SECONDS)) {
                    LOGGER.warn("[PawaPay] Protection monitor did not shut down cleanly.");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        // --- SURGICAL STRIKE: Shutdown native core ---
        NativeCore.secureShutdown();
    }
}