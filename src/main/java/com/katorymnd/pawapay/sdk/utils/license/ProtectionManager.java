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
 * Handles license compliance tracking and progressive degradation policies.
 * Coordinates with VM interpreter for runtime policy decisions.
 */
public class ProtectionManager implements DegradationManager.ProtectionLayer {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProtectionManager.class);

    // Singleton instance
    private static final ProtectionManager INSTANCE = new ProtectionManager();

    // =========================================================================
    // INTERNAL STATE FLAGS (LEGACY DEBUG)
    // Retained for backward compatibility  diagnostic tools.
    // These flags control verbose logging and sandbox bypasses for 
    // internal testing environments. DO NOT MODIFY in production builds.
    // 
    // =========================================================================
    public static boolean ignoreViolations = false;
    public static boolean preventDestruction = false;
    // =========================================================================

    
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
     * Reconciles local violation state with platform-layer persistence.
     * Used to restore state after process restarts or environment changes.
     */
    private void attemptNativeHealing() {
        try {
            boolean healed = NativeCore.secureAttemptHealing();
            if (healed) {
                LOGGER.info("[PawaPay] Native core self-healing successful - violations cleared or reduced");
                // Sync local cache with platform-layer state
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
           // Attempt state reconciliation even in terminal state
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
            
           // Pass canonical violation count to VM for policy evaluation
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
        
         // Update local cache
        violations.add(violation);
        lastViolationTime = now;
        lastNormalUseTime = now;
        consecutiveSuccesses = 0;

        // =================================================================
        // CROSS-PLATFORM STATE SYNCHRONIZATION
        // Propagates violation state to platform layer for canonical
        // persistence. Ensures consistency across JVM restarts and
        // multi-process deployments.
        // =================================================================
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
            // Attempt state reconciliation - successes may reduce violation count
            attemptNativeHealing();
            if (!isDestroyed()) {
                LOGGER.info("[PawaPay] SDK self-healed after successful requests");
            } else {
                return;
            }
        }

        pruneExpiredViolations();
        lastNormalUseTime = System.currentTimeMillis() / 1000;

        // =================================================================
        // CROSS-PLATFORM STATE SYNCHRONIZATION
        // Propagates success metrics to platform layer for canonical
        // persistence and recovery policy evaluation.
        // =================================================================
        boolean tampered = ignoreViolations || preventDestruction;
        NativeCore.secureRecordSuccess(consecutiveSuccesses, tampered);

         // Local cache behavior for backward compatibility
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

    // Set local terminal state
    destroyed = true;

    // =================================================================
    // CROSS-PLATFORM STATE SYNCHRONIZATION
    // Propagates terminal state to platform layer for canonical
    // persistence. Ensures consistent teardown across JVM restarts.
    // =================================================================
    boolean tampered = preventDestruction || ignoreViolations;
    NativeCore.secureTriggerDestruction(tampered);

    if (!Boolean.TRUE.equals(options.get("silent"))) {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║  PawaPay SDK License Violation - SDK Disabled                ║");
        System.out.println("║  Contact: support@katorymnd.com                              ║");
        System.out.println("║  Purchase: https://katorymnd.com/pawapay-payment-sdk/java/   ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
    }
}

    public void destroy() {
        destroy(null);
    }

    public boolean isDestroyed() {
        // =================================================================
        // CROSS-PLATFORM STATE SYNCHRONIZATION
        // Queries platform layer for canonical terminal state.
        // Local cache is secondary; platform layer is source of truth.
        // =================================================================
        boolean tampered = preventDestruction || ignoreViolations;
        return NativeCore.secureIsDestroyed(this.destroyed, tampered);
    }

    // --- Interface Methods for DegradationManager.ProtectionLayer ---
    
    @Override
    public synchronized int getViolationCount() {
        pruneExpiredViolations();
        
        // =================================================================
        // CROSS-PLATFORM STATE SYNCHRONIZATION
        // Queries platform layer for canonical violation count.
        // Local cache is secondary; platform layer is source of truth.
        // =================================================================
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
       // Clear local cache
        violations.clear();
        consecutiveSuccesses = 0;
        long now = System.currentTimeMillis() / 1000;
        lastViolationTime = now;
        lastNormalUseTime = now;

        // Reset platform-layer state for consistency
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
           // Attempt state reconciliation first
            attemptNativeHealing();
            
            if (!violations.isEmpty() && !isDestroyed()) {
                Iterator<Map<String, Object>> iterator = violations.iterator();
                while (iterator.hasNext()) {
                    Map<String, Object> v = iterator.next();
                    if ((now - ((Number) v.get("timestamp")).longValue()) > 7 * 24 * 60 * 60) { 
                        iterator.remove();
                        // Notify platform layer to update recovery metrics
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
            // Attempt state reconciliation before failing
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
        
        // =================================================================
        // CROSS-PLATFORM STATE SYNCHRONIZATION
        // Notifies platform layer of JVM shutdown for canonical cleanup.
        // Ensures consistent state across restarts.
        // =================================================================
        NativeCore.secureShutdown();
    }
}