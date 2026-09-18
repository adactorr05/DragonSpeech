package com.dragonspeech.stamina;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Shared "take energy from a living thing" logic - used by aflsuga
 * (draining one target) and by "brynna" (draining several nearby things
 * to fund a whole other spell). Same rule for every target regardless of
 * which spell is asking: stamina first (real, for a player; the
 * persistent notional MobStaminaAccess reserve otherwise), THEN
 * (players only) hunger, and only once BOTH of those are exhausted does
 * it reach for health - and reaching health is deliberately inefficient
 * in both directions (poor damage per unit requested, and poor stamina
 * credit per unit of health taken), so a caster leaning on this past a
 * target's real reserves gets a bad trade, not a free lunch.
 *
 * This is NOT the same cascade as DrainResolver (which pays for the
 * CASTER's own spells, in a specific order tuned around item storage/
 * Eldunari/dragon assist that has no business existing on an arbitrary
 * mob) - it's deliberately a separate, simpler tier ladder that only
 * ever runs against something OTHER than the caster.
 */
public final class LifeForceDrain {

    /** How much real health damage one point of "requested but only available via life force" energy deals - intentionally small. */
    private static final float HEALTH_DAMAGE_PER_ENERGY = 0.15f;
    /** Of whatever health damage actually happens, how much of it comes back as usable energy for whoever's drawing on it - intentionally smaller still, so leaning on life force is a bad trade twice over, not once. */
    private static final float ENERGY_CREDIT_PER_HEALTH_DAMAGE = 0.5f;
    /** How much hunger (out of 20) one point of energy costs a player target - matches DrainResolver's own HUNGER_ENERGY_PER_POINT inverse so a target and a caster spend hunger at the same real rate. */
    private static final float HUNGER_ENERGY_PER_POINT = 5.0f;

    private LifeForceDrain() {}

    public record Outcome(float energyGathered, float healthDamageDealt) {}

    /**
     * Draws up to `requestedEnergy` worth of "fuel" out of `target`,
     * actually applying the stamina/hunger loss and any health damage as
     * it goes (this is NOT a dry-run calculation - by the time this
     * returns, the target has genuinely been drained). Returns how much
     * energy was actually gathered (may be less than requested if the
     * target ran completely dry) and how much real health damage was
     * dealt in the process, if any.
     */
    public static Outcome drain(LivingEntity target, float requestedEnergy, ServerLevel level) {
        if (requestedEnergy <= 0f) {
            return new Outcome(0f, 0f);
        }
        long now = level.getGameTime();
        float remaining = requestedEnergy;
        float gathered = 0f;

        // --- Tier 1: stamina (real for a player, notional-but-persistent for anything else) ---
        float availableStamina = target instanceof ServerPlayer player
            ? StaminaAccess.get(player).stamina()
            : MobStaminaAccess.get(target, now);
        float staminaTaken = Math.min(remaining, availableStamina);
        if (staminaTaken > 0f) {
            if (target instanceof ServerPlayer player) {
                PlayerMagicData data = StaminaAccess.get(player);
                StaminaAccess.set(player, data.withStamina(data.stamina() - staminaTaken));
            } else {
                MobStaminaAccess.spend(target, staminaTaken, now);
            }
            gathered += staminaTaken;
            remaining -= staminaTaken;
        }

        // --- Tier 2: hunger (players only - mobs have no hunger to speak of) ---
        if (remaining > 0f && target instanceof ServerPlayer player) {
            float hungerAvailableEnergy = player.getFoodData().getFoodLevel() * HUNGER_ENERGY_PER_POINT;
            float hungerEnergyTaken = Math.min(remaining, hungerAvailableEnergy);
            if (hungerEnergyTaken > 0f) {
                int hungerPointsToConsume = (int) Math.ceil(hungerEnergyTaken / HUNGER_ENERGY_PER_POINT);
                var foodData = player.getFoodData();
                foodData.setFoodLevel(Math.max(0, foodData.getFoodLevel() - hungerPointsToConsume));
                gathered += hungerEnergyTaken;
                remaining -= hungerEnergyTaken;
            }
        }

        // --- Tier 3: life force - real damage, deliberately inefficient both ways ---
        float healthDamageDealt = 0f;
        if (remaining > 0f) {
            healthDamageDealt = remaining * HEALTH_DAMAGE_PER_ENERGY;
            if (healthDamageDealt > 0f) {
                target.hurt(level.damageSources().magic(), healthDamageDealt);
                gathered += healthDamageDealt * ENERGY_CREDIT_PER_HEALTH_DAMAGE;
            }
        }

        return new Outcome(gathered, healthDamageDealt);
    }
}
