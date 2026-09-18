package com.dragonspeech.engine;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A single, shared answer to "is this entity currently time-slowed?",
 * fed by BOTH the instant single-target working (TemporalWorkingHandler)
 * and the lingering bubble (TemporalFieldManager) - whichever one
 * touched an entity last just refreshes the same entry. This is what
 * lets TimeSlowDamageHooks treat "slowed by a bolt" and "standing inside
 * a kringla field" identically, without either system needing to know
 * the other exists.
 *
 * Stasis counts as slowed too - something fully outside time's flow is,
 * if anything, MORE insulated from an explosion than something merely
 * sluggish, not less.
 *
 * In-memory only, like every other held-effect tracker in this mod - an
 * entry that outlives a server restart was never meaningful anyway.
 */
public final class TimeSlowRegistry {

    private static final Map<UUID, Long> SLOWED_UNTIL_TICK = new HashMap<>();

    private TimeSlowRegistry() {}

    public static void mark(UUID entityId, long nowTick, int durationTicks) {
        SLOWED_UNTIL_TICK.merge(entityId, nowTick + durationTicks, Math::max);
    }

    public static boolean isSlowed(UUID entityId, long nowTick) {
        Long until = SLOWED_UNTIL_TICK.get(entityId);
        return until != null && until > nowTick;
    }

    /** Called occasionally by whichever system finds it convenient - keeps the map from growing without bound over a long session. Safe to call as often or as rarely as you like; isSlowed() is correct either way. */
    public static void pruneExpired(long nowTick) {
        SLOWED_UNTIL_TICK.entrySet().removeIf(entry -> entry.getValue() <= nowTick);
    }

    /**
     * The "fire" part of "slow explosions, fire, damage, etc.": if this
     * entity is currently burning, stretch out how much longer it burns
     * for, without changing the per-tick damage rate. From the outside
     * that reads exactly as "fire affects them more slowly" - the same
     * total harm, spread over more real time - achieved with a plain
     * entity method rather than touching FireBlock's tick logic itself.
     */
    public static void stretchFireIfBurning(net.minecraft.world.entity.LivingEntity entity, int extraTicks) {
        if (entity.getRemainingFireTicks() > 0) {
            entity.setRemainingFireTicks(entity.getRemainingFireTicks() + extraTicks);
        }
    }
}
