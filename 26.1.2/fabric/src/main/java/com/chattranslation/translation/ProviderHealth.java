package com.chattranslation.translation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Per-provider health tracking with a circuit breaker.
 *
 * <p>Motivation from real measurements: the bundled free endpoint alternates between ~3s and
 * ~34s responses. When a provider starts failing or stalling, the previous behaviour was to keep
 * hammering it with every chat line, which multiplied latency and burned the shared quota.
 *
 * <p>After {@link #FAILURE_THRESHOLD} consecutive failures a provider is marked <em>open</em> for
 * {@link #BASE_COOLDOWN_MS}, growing up to {@link #MAX_COOLDOWN_MS} while it keeps failing. While
 * open, calls fail fast instead of hitting the network, so the chat keeps flowing with original
 * text (or the fallback provider) rather than queueing behind a dead endpoint.
 *
 * <p>A success resets the breaker immediately, so recovery is instant once the service returns.
 */
public final class ProviderHealth {

    private static final int FAILURE_THRESHOLD = 3;
    private static final long BASE_COOLDOWN_MS = 20_000L;
    private static final long MAX_COOLDOWN_MS = 5 * 60_000L;

    private static final Map<String, ProviderHealth> REGISTRY = new ConcurrentHashMap<>();

    private final AtomicLong consecutiveFailures = new AtomicLong();
    private final AtomicLong openUntilMillis = new AtomicLong();
    private final AtomicLong totalFailures = new AtomicLong();
    private final AtomicLong totalSuccesses = new AtomicLong();
    private volatile long currentCooldown = BASE_COOLDOWN_MS;

    public static ProviderHealth of(String providerName) {
        return REGISTRY.computeIfAbsent(providerName == null ? "unknown" : providerName,
                key -> new ProviderHealth());
    }

    /** @return true when the provider is cooling down and should not be contacted */
    public boolean isOpen() {
        long until = openUntilMillis.get();
        if (until == 0L) {
            return false;
        }
        if (System.currentTimeMillis() >= until) {
            // Cooldown elapsed: allow a single probe through.
            openUntilMillis.compareAndSet(until, 0L);
            return false;
        }
        return true;
    }

    /** @return remaining cooldown in milliseconds, for logging */
    public long remainingCooldownMillis() {
        return Math.max(0L, openUntilMillis.get() - System.currentTimeMillis());
    }

    public void recordSuccess() {
        totalSuccesses.incrementAndGet();
        consecutiveFailures.set(0L);
        openUntilMillis.set(0L);
        currentCooldown = BASE_COOLDOWN_MS;
    }

    /**
     * @param permanent true for errors a retry cannot fix (bad key, bad model, 4xx) so the breaker
     *                  opens immediately instead of waiting for three identical failures
     */
    public void recordFailure(boolean permanent) {
        totalFailures.incrementAndGet();
        long failures = consecutiveFailures.incrementAndGet();
        if (!permanent && failures < FAILURE_THRESHOLD) {
            return;
        }
        openUntilMillis.set(System.currentTimeMillis() + currentCooldown);
        currentCooldown = Math.min(MAX_COOLDOWN_MS, currentCooldown * 2);
    }

    /** Compact state for logs and the config screen. */
    public String describe() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("failures", totalFailures.get());
        state.put("successes", totalSuccesses.get());
        state.put("open", isOpen());
        state.put("cooldownMs", remainingCooldownMillis());
        return state.toString();
    }
}
