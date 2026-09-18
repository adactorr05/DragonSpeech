package com.dragonspeech.stamina;

import net.minecraft.world.entity.LivingEntity;

/**
 * Thin wrapper around the mob stamina reserve attachment, same role
 * StaminaAccess plays for players - except this also has to compute
 * passive regen lazily (there's no per-mob tick hook driving this, so
 * "how much has regenerated" is worked out from elapsed game time only
 * when something actually asks, the same general idea GravityFieldManager
 * and friends use ticks for, just without needing an actual tick
 * registration since nothing needs to happen when nobody's watching).
 *
 * Max size and regen rate now come from MobStaminaScaling (per-entity-
 * type, not one flat number for everything) - see that class's own doc
 * for why. Every caller here (get/spend) already just passed the entity
 * through, so this changed with zero signature changes for existing
 * callers (LifeForceDrain, etc.) - only the numbers used internally.
 */
public final class MobStaminaAccess {

    private MobStaminaAccess() {}

    /** The entity's CURRENT reserve, with any passive regen since it was last touched already accounted for. */
    public static float get(LivingEntity entity, long nowGameTime) {
        float max = MobStaminaScaling.maxFor(entity);
        MobStaminaReserve stored = entity.getAttached(MobStaminaAttachments.RESERVE);
        if (stored == null) {
            return max; // never drained - assume full
        }
        long elapsed = Math.max(0, nowGameTime - stored.lastUpdateGameTime());
        float regenPerTick = MobStaminaScaling.regenPerSecond(entity) / 20f;
        float regenerated = stored.reserve() + elapsed * regenPerTick;
        return Math.min(max, regenerated);
    }

    /** Spends `amount` (already clamped by the caller to what get() reported as available) and persists the result with a fresh timestamp. */
    public static void spend(LivingEntity entity, float amount, long nowGameTime) {
        float current = get(entity, nowGameTime);
        float remaining = Math.max(0f, current - amount);
        entity.setAttached(MobStaminaAttachments.RESERVE, new MobStaminaReserve(remaining, nowGameTime));
    }
}
