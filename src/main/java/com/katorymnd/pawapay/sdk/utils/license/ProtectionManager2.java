// src/main/java/com/katorymnd/pawapay/sdk/utils/license/ProtectionManager.java
package com.katorymnd.pawapay.sdk.utils.license;

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

    // Core state
    private boolean destroyed = false;
    private final List<Map<String, Object>> violations = Collections.synchronizedList(new ArrayList<>());
    private final int maxViolations = 3;

    // Success-based recovery configuration
    private int successThreshold = 5;
    private int consecutiveSuccesses = 0;

    // Development safety
    private boolean nonDestructiveMode = false;

    // Automatic time based expiry (seconds)
    private long violationExpirySeconds = 0; // 0 to disable

    // Time-based state (in seconds)
    private final long initializedAt;
    private long lastHealthCheck;
    private long lastViolationTime;
    private long lastNormalUseTime;

    // Time-based decay properties
    @SuppressWarnings("unused")
    private final long decayInterval = 24 * 60 * 60; // 24 hours per decay
    private final long decayThreshold = 7 * 24 * 60 * 60; // 7 days of normal use reduces violations by 1

    // Random seed for deterministic behavior
    private final int randomSeed;

    private ScheduledExecutorService decayMonitorService;

    private ProtectionManager() {
        long now = System.currentTimeMillis() / 1000;
        this.initializedAt = now;
        this.lastHealthCheck = now;
        this.lastViolationTime = now;
        this.lastNormalUseTime = now;

        this.randomSeed = new Random().nextInt(1001); // 0 to 1000

        startDecayMonitor();
    }

    public static ProtectionManager getInstance() {
        return INSTANCE;
    }

    private void startDecayMonitor() {
        // Start background thread to monitor decay, running once an hour
        decayMonitorService = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "PawaPay-DecayMonitor");
            t.setDaemon(true);
            return t;
        });
        
        decayMonitorService.scheduleAtFixedRate(this::checkAndApplyDecay, 1, 1, TimeUnit.HOURS);
    }

    private synchronized void checkAndApplyDecay() {
        if (destroyed) {
            return;
        }

        long now = System.currentTimeMillis() / 1000;
        long timeSinceLastViolation = now - lastViolationTime;
        long timeSinceNormalUse = now - lastNormalUseTime;

        // Apply automatic time-based decay
        performHealthCheck();

        // Additional decay based on continuous normal use
        if (!violations.isEmpty() && timeSinceNormalUse >= decayThreshold) {
            int oldViolationCount = violations.size();

            // Remove the oldest violation
            violations.remove(0);
            LOGGER.info("[PawaPay] Time-based decay: Violations reduced from {} to {}", oldViolationCount, violations.size());

            // Reset the normal use timer
            lastNormalUseTime = now;
        }

        // Bonus decay for extended good behavior
        if (timeSinceLastViolation > (30L * 24 * 60 * 60)) { // 30 days
            if (!violations.isEmpty()) {
                LOGGER.info("[PawaPay] 30-day good behavior reset: Clearing {} violations", violations.size());
                violations.clear();
                consecutiveSuccesses = 0;
            }
        }
    }

    private synchronized void pruneExpiredViolations() {
        if (violationExpirySeconds == 0 || violations.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis() / 1000;
        violations.removeIf(v -> {
            long timestamp = ((Number) v.get("timestamp")).longValue();
            return (now - timestamp) > violationExpirySeconds;
        });
    }

    private boolean shouldDestroyViaVm() {
        /*
         * Ask VM whether SDK should be destroyed or degraded
         * VM owns the decision, Java only executes the outcome
         */
        try {
            long now = System.currentTimeMillis() / 1000;

            Map<String, Object> vmContext = new HashMap<>();
            vmContext.put("violations", violations.size());
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

            // Handle degradation via manager
            DegradationManager degradationManager = new DegradationManager(this);
            degradationManager.handleVmDegradationDecision(decision);

            return decision == 2; // Return true only if VM says DESTROY

        } catch (Exception err) {
            LOGGER.error("[PawaPay][VM] VM execution failed, entering degraded mode: {}", err.getMessage());
            return false;
        }
    }

    public synchronized void recordViolation(String reason, Map<String, Object> options) {
        if (options == null) {
            options = new HashMap<>();
        }

        boolean silent = Boolean.TRUE.equals(options.get("silent"));

        // Silent failure mode: don't log if specified
        if (!silent) {
            LOGGER.warn("[PawaPay License Violation] {}", reason);
        }

        // Prune first if expiry set
        pruneExpiredViolations();

        long now = System.currentTimeMillis() / 1000;
        Map<String, Object> violation = new HashMap<>();
        violation.put("reason", reason);
        violation.put("timestamp", now);
        violation.put("silent", silent);
        
        violations.add(violation);

        // Update violation timestamp for decay tracking
        lastViolationTime = now;
        lastNormalUseTime = now; // Reset decay timer

        // Reset consecutive successes when a violation occurs
        consecutiveSuccesses = 0;

        // Ask VM whether destruction should occur
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
        if (destroyed) {
            return;
        }

        pruneExpiredViolations();

        // Update last normal use time for decay tracking
        lastNormalUseTime = System.currentTimeMillis() / 1000;

        // If no violations, nothing to recover
        if (violations.isEmpty()) {
            consecutiveSuccesses = 0;
            return;
        }

        consecutiveSuccesses++;

        // Random chance of faster recovery with consistent success
        if (consecutiveSuccesses >= successThreshold / 2) {
            double recoveryChance = 0.1; // 10% chance per successful batch
            if (Math.random() < recoveryChance) {
                if (!violations.isEmpty()) {
                    violations.remove(0);
                    LOGGER.info("[PawaPay] Good behavior accelerated recovery: {} violations remaining", violations.size());
                    consecutiveSuccesses = successThreshold / 2;
                }
            }
        }

        // Original threshold logic
        if (consecutiveSuccesses >= successThreshold) {
            // Remove oldest violation (FIFO)
            if (!violations.isEmpty()) {
                violations.remove(0);

                LOGGER.info("[PawaPay] Forgave one violation after {} consecutive valid requests. Remaining violations: {}", 
                    successThreshold, violations.size());

                // Reset consecutive counter after forgiveness
                consecutiveSuccesses = 0;
            }
        }
    }

    public synchronized void destroy(Map<String, Object> options) {
        if (destroyed) {
            return;
        }

        if (options == null) {
            options = new HashMap<>();
        }

        destroyed = true;

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
        return destroyed;
    }

    // --- Interface Methods for DegradationManager.ProtectionLayer ---
    
    @Override
    public synchronized int getViolationCount() {
        pruneExpiredViolations();
        return violations.size();
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
        violations.clear();
        consecutiveSuccesses = 0;
        long now = System.currentTimeMillis() / 1000;
        lastViolationTime = now;
        lastNormalUseTime = now;

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
        long timeSinceLastCheck = now - lastHealthCheck;

        // Automatic recovery over time (optional)
        if (timeSinceLastCheck > 24 * 60 * 60) { // 24 hours
            if (!violations.isEmpty() && !destroyed) {
                // Forgive one old violation per day
                Iterator<Map<String, Object>> iterator = violations.iterator();
                while (iterator.hasNext()) {
                    Map<String, Object> v = iterator.next();
                    long timestamp = ((Number) v.get("timestamp")).longValue();
                    if ((now - timestamp) > 7 * 24 * 60 * 60) { // 7 days old
                        iterator.remove();
                        LOGGER.info("[PawaPay] Auto-forgave one old violation due to time decay");
                        break;
                    }
                }
            }
        }
        lastHealthCheck = now;
    }

    /**
     * Wrap API calls with silent degradation using CompletableFuture (async equivalent)
     */
    public <T> CompletableFuture<T> callWithDegradation(String callId, Callable<CompletableFuture<T>> apiCall) {
        if (destroyed) {
            CompletableFuture<T> future = new CompletableFuture<>();
            future.completeExceptionally(new Exception("SDK is disabled due to license violations"));
            return future;
        }

        try {
            // Degradation manager logic would wrap this. For now, executing directly.
            DegradationManager dm = new DegradationManager(this);
            return dm.applyDegradation(callId, apiCall);
        } catch (Exception e) {
            CompletableFuture<T> future = new CompletableFuture<>();
            future.completeExceptionally(e);
            return future;
        }
    }

    /**
     * Stop the background decay monitoring thread.
     * This is critical for clean shutdowns in applications and tests.
     */
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
    }
}