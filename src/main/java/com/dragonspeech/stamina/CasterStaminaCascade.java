package com.dragonspeech.stamina;

import com.dragonspeech.config.DragonSpeechConfig;
import net.minecraft.server.level.ServerPlayer;

/**
 * The "drain the CASTER's own reserves" cascade - stamina, then hunger,
 * then health down to whatever floor DragonSpeechConfig.minSurvivableHealth()
 * currently allows (0 on Magic Difficulty HARD, meaning this can
 * genuinely kill them). Used by anything that's stamina-LINKED rather
 * than target-draining (LifeForceDrain) or self-charging
 * (ChargeItemEffectHandler) - originally built inside
 * MagicBarrierEntity for aflbinda-linked barriers, now extracted here so
 * stamina-linked WARD enchantments (see MagicWardCombat) can share the
 * exact same behavior instead of a second, potentially-drifting copy of
 * the same three tiers.
 *
 * DELIBERATELY NOT the same as WardService's existing stamina-bound
 * quick-ward absorption, which calls DrainResolver.applyLethalDrain
 * directly (always lethal, no floor, regardless of Magic Difficulty -
 * a pre-existing design for that lighter-weight system). This class is
 * for anything that's supposed to respect the current Magic Difficulty
 * floor instead, per the explicit spec for item-enchanted wards: "drain
 * until you run out of health (half a heart) and the word will
 * collapse... if in Hard mode, it could kill you."
 */
public final class CasterStaminaCascade {

    private static final float HUNGER_ENERGY_PER_POINT = 5.0f;
    private static final float HEALTH_ENERGY_PER_POINT = 2.0f;

    private CasterStaminaCascade() {}

    /**
     * Drains up to `amount` energy from stamina, then hunger, then
     * health-down-to-the-floor, in that order. Returns whatever's STILL
     * uncovered after all three (0 if fully covered) - the caller
     * decides what to do with that remainder (fall through to a shield's
     * own strength, to a ward's own durability, or just go unabsorbed).
     */
    public static float drain(ServerPlayer caster, float amount) {
        float remaining = amount;

        PlayerMagicData data = StaminaAccess.get(caster);
        float staminaSpent = Math.min(data.stamina(), remaining);
        if (staminaSpent > 0f) {
            StaminaAccess.set(caster, data.withStamina(data.stamina() - staminaSpent));
            remaining -= staminaSpent;
        }

        if (remaining > 0f) {
            float hungerAvailableEnergy = caster.getFoodData().getFoodLevel() * HUNGER_ENERGY_PER_POINT;
            float hungerUsed = Math.min(remaining, hungerAvailableEnergy);
            if (hungerUsed > 0f) {
                int hungerPointsToConsume = (int) Math.ceil(hungerUsed / HUNGER_ENERGY_PER_POINT);
                var foodData = caster.getFoodData();
                foodData.setFoodLevel(Math.max(0, foodData.getFoodLevel() - hungerPointsToConsume));
                remaining -= hungerUsed;
            }
        }

        if (remaining > 0f) {
            float survivableHealth = Math.max(0f, caster.getHealth() - DragonSpeechConfig.minSurvivableHealth());
            float healthAvailableEnergy = survivableHealth * HEALTH_ENERGY_PER_POINT;
            float healthUsed = Math.min(remaining, healthAvailableEnergy);
            if (healthUsed > 0f) {
                float healthPointsToConsume = healthUsed / HEALTH_ENERGY_PER_POINT;
                caster.setHealth(Math.max(DragonSpeechConfig.minSurvivableHealth(), caster.getHealth() - healthPointsToConsume));
                remaining -= healthUsed;
            }
        }

        return remaining;
    }
}
