package com.dragonspeech.engine;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;

/**
 * "It slows movement, but I also want it to slow explosions, fire,
 * damage, etc." - this is the explosions/general-damage half.
 * (Fire specifically is handled separately, in TemporalFieldManager -
 * see stretchFireIfBurning and the periodic fire-thinning there.)
 *
 * Implemented as a partial HEAL-BACK after damage lands, not as a
 * blocked/reduced hit at the moment of impact. That's deliberate: this
 * fires from ServerLivingEntityEvents.AFTER_DAMAGE, which runs once the
 * real damage has already been fully processed - safe to act on freely,
 * with none of the reentrancy risk of trying to intercept or shrink
 * damage from inside the same event that's still deciding whether it
 * happens at all (the same reasoning DrainResolver.applyLethalDrain's
 * one-tick-delayed queue exists for, just solved differently here since
 * heal() itself is already safe to call directly).
 *
 * An explosion within a time-slow field doesn't visually happen in slow
 * motion (that would need mixin-level interception of Level.explode()
 * itself, which is far riskier, core-engine territory this project has
 * deliberately avoided everywhere else) - but its FORCE reaching a
 * slowed target is meaningfully dampened, which is the part that
 * actually matters for "protect the village from a creeper."
 */
public final class TimeSlowDamageHooks {

    private static final float EXPLOSION_REDUCTION = 0.40f;
    private static final float GENERAL_REDUCTION = 0.15f;

    private TimeSlowDamageHooks() {}

    public static void register() {
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseAmount, actualAmount, blocked) -> {
            if (blocked || actualAmount <= 0f || !entity.isAlive() || !(entity.level() instanceof ServerLevel level)) {
                return;
            }

            long now = level.getGameTime();
            if (!TimeSlowRegistry.isSlowed(entity.getUUID(), now)) {
                return;
            }

            float fraction = source.is(DamageTypeTags.IS_EXPLOSION) ? EXPLOSION_REDUCTION : GENERAL_REDUCTION;
            float refund = actualAmount * fraction;
            if (refund > 0f) {
                entity.heal(refund);
            }
        });
    }
}
