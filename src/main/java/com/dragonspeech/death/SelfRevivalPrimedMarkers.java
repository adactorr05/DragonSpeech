package com.dragonspeech.death;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * "aftrlifga sjalfan" spoken ALIVE with no binding word in the sentence
 * primes a short 5-second self-revival window rather than a lasting
 * ward - timing substitutes for the ward-form's premium. If death comes
 * within the window, DeathHooks catches it automatically; if not, the
 * priming simply expires with no effect and no lingering binding.
 */
public final class SelfRevivalPrimedMarkers {

    public static final long WINDOW_TICKS = 20L * 5;

    private static final Map<UUID, Long> primedUntil = new HashMap<>();

    private SelfRevivalPrimedMarkers() {}

    public static void prime(UUID playerId, long nowGameTime) {
        primedUntil.put(playerId, nowGameTime + WINDOW_TICKS);
    }

    /** True and clears the priming if this player died within their window. */
    public static boolean consumeIfActive(UUID playerId, long nowGameTime) {
        Long expiry = primedUntil.remove(playerId);
        return expiry != null && nowGameTime <= expiry;
    }
}
