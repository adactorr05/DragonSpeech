package com.dragonspeech.engine;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Backs the Force domain's gravity ladder (thyngja / thyngdbinda) - a
 * REAL per-tick gravity multiplier, not a re-skinned Slow Falling or
 * Levitation potion effect. Every affected entity gets extra downward
 * (or upward-canceling) velocity added on top of whatever vanilla's own
 * gravity already did that tick, so fall speed, jump arc, and "how hard
 * it is to move against it" all come from the SAME single number -
 * genuinely simulating heavier/lighter gravity rather than faking one
 * specific symptom of it.
 *
 * Same in-memory, dissipates-on-restart model as StasisManager/
 * TimeSlowRegistry: nothing here is meant to survive a crash or persist
 * across sessions, so there's no save/load path and there shouldn't be one.
 *
 * DELIBERATELY NOT using Minecraft's Attributes.GRAVITY (a real vanilla
 * attribute that DOES exist in some 1.21.x builds and would arguably be
 * the more "proper" way to do this) - this project has no compiler
 * available to confirm that attribute's exact presence/name for your
 * specific 1.21.1 build, and after several rounds of exactly that kind
 * of guess costing you failed builds already, this manual per-tick
 * velocity approach was chosen instead because it only uses methods
 * (getDeltaMovement/setDeltaMovement, onGround) already proven to
 * compile in THIS project's own StasisManager/PushEffectHandler. If you
 * want to try the attribute-based version later for a cleaner
 * integration with vanilla's own physics code, that's a reasonable
 * follow-up, not a correction of this one.
 */
public final class GravityFieldManager {

    /** Roughly vanilla's own per-tick gravity constant for most living entities - the baseline this multiplies against, not a value read from anywhere vanilla. */
    private static final double BASE_GRAVITY = 0.08;

    /** However long a fall/float goes on, total vertical speed never exceeds this - keeps extreme multipliers from clipping through floors or launching someone into the stratosphere. */
    private static final double MAX_FALL_SPEED = 6.0;
    private static final double MAX_RISE_SPEED = 3.0;

    /** At/above this multiplier, an upward jump impulse gets crushed down instead of carrying normally - "forced to your knees, fighting gravity just to move" rather than merely falling back a little faster. */
    private static final float HEAVY_JUMP_CRUSH_THRESHOLD = 2.0f;

    private record GravityState(ResourceKey<Level> dimension, UUID entityId, float multiplier, long expiresAtTick) {}

    private static final Map<UUID, GravityState> ACTIVE = new HashMap<>();

    private GravityFieldManager() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(GravityFieldManager::tick);
    }

    /**
     * Applies (or refreshes/replaces) a gravity multiplier on a target.
     * 1.0 = normal, greater = heavier, less = lighter, 0 = weightless,
     * negative = actively floats upward. Re-casting on an already-affected
     * entity replaces the old multiplier/duration rather than stacking -
     * gravity doesn't have a "more of it at once" that means anything.
     */
    public static void apply(ServerLevel level, LivingEntity target, float multiplier, int durationTicks) {
        long now = level.getServer().getTickCount();
        ACTIVE.put(target.getUUID(), new GravityState(level.dimension(), target.getUUID(), multiplier, now + durationTicks));
    }

    public static boolean isAffected(UUID entityId) {
        return ACTIVE.containsKey(entityId);
    }

    private static void tick(MinecraftServer server) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        long now = server.getTickCount();

        List<UUID> expired = new ArrayList<>();
        for (Iterator<Map.Entry<UUID, GravityState>> it = ACTIVE.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, GravityState> entry = it.next();
            GravityState state = entry.getValue();

            if (now >= state.expiresAtTick()) {
                it.remove();
                continue;
            }

            ServerLevel level = server.getLevel(state.dimension());
            if (level == null) {
                continue;
            }
            if (!(level.getEntity(state.entityId()) instanceof LivingEntity target) || !target.isAlive()) {
                expired.add(entry.getKey());
                continue;
            }
            if (target.isNoGravity()) {
                continue; // something else (stasis, etc.) already owns this entity's vertical motion this tick
            }

            applyOneTick(target, state.multiplier());
        }
        expired.forEach(ACTIVE::remove);
    }

    private static void applyOneTick(LivingEntity target, float multiplier) {
        Vec3 motion = target.getDeltaMovement();
        double extra = -BASE_GRAVITY * (multiplier - 1.0);
        double newY = motion.y + extra;

        // Heavier gravity doesn't just fall faster - it should genuinely
        // fight horizontal movement too ("forced to your knees, fighting
        // gravity just to move forward"), and lighter gravity should
        // carry you further per step, the same real number driving both.
        // At multiplier 2.6 ("mikla nidra") walking speed drops to
        // roughly 38%; at the 6.0 cap it's crushed to ~17% - "can barely
        // move" territory, exactly what stacking magnitude words onto
        // this should buy you.
        double horizontalScale = multiplier >= 1f
            ? 1.0 / Math.max(multiplier, 1.0)
            : Math.min(2.0, 1.0 + (1.0 - multiplier));
        double newX = motion.x * horizontalScale;
        double newZ = motion.z * horizontalScale;

        // Heavy gravity crushes a jump back down instead of letting it
        // carry - only while actually ascending, so it doesn't also
        // stomp on ordinary falling (which should stay fast, not slam
        // to zero).
        if (multiplier >= HEAVY_JUMP_CRUSH_THRESHOLD && newY > 0) {
            newY /= multiplier;
        }

        newY = Math.max(-MAX_FALL_SPEED, Math.min(MAX_RISE_SPEED, newY));
        target.setDeltaMovement(newX, newY, newZ);
        target.hurtMarked = true; // ensures the velocity change actually reaches the client, same as PushEffectHandler
        // Fall damage is deliberately left to vanilla's own rules here -
        // heavier gravity means falling faster, which naturally means
        // harder landings, and that's the right consequence, not
        // something to suppress.
    }
}
