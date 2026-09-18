package com.dragonspeech.stamina;

/**
 * What a drain actually cost the player, broken down by pool. overdrafted
 * being true means even item reserves + stamina + hunger + survivable
 * health together couldn't cover the cost - the caster is drained dry and
 * the cast should NOT proceed (see CastExecutor).
 */
public record DrainResult(
    float itemEnergySpent,
    float staminaSpent,
    float hungerEnergySpent,
    float healthEnergySpent,
    boolean overdrafted
) {
    public boolean succeeded() {
        return !overdrafted;
    }
}
