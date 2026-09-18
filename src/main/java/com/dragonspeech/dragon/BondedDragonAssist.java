package com.dragonspeech.dragon;

import net.minecraft.server.level.ServerPlayer;

/**
 * "This limiter works if you have checkbox_9 (Use dragon stamina)
 * enabled. It should also check the slider for stamina usage
 * before/after your own - this allows for you to limit how much
 * stamina is drawn on before it stops drawing stamina and
 * continues/uses your own stamina." Called from DrainResolver at TWO
 * different points in the cascade (before or after the player's own
 * stamina tier) depending on DragonEntity#staminaBeforeOwn() - see the
 * call sites there for why it's two call sites instead of one
 * reorderable tier.
 */
public final class BondedDragonAssist {

    private static final double RANGE = 32.0;

    private BondedDragonAssist() {}

    /**
     * Drains up to `remaining` from the player's nearest bonded dragon
     * (if useDragonStamina() is on), never taking the dragon below its
     * own configured limiter floor. Returns how much was actually
     * drawn - 0 if no eligible dragon is in range or the feature is off.
     *
     * Blessing of the Bonded Wing ("vaengheill", worn by the PLAYER, not
     * the dragon) - "improves how efficiently BondedDragonAssist draws
     * from your dragon" - lets the wearer reach 5% deeper into the
     * dragon's own reserves per level (up to 2 levels = 10%) before the
     * dragon's own limiter floor stops the draw. The dragon's own
     * configured floor is still respected as the baseline; this only
     * ever narrows the protected margin, never removes it outright.
     */
    public static float drain(ServerPlayer player, float remaining) {
        if (remaining <= 0f) {
            return 0f;
        }
        var bonded = DragonEntity.findNearestBonded(player, RANGE);
        if (bonded.isEmpty()) {
            return 0f;
        }
        DragonEntity dragon = bonded.get();
        if (!dragon.useDragonStamina()) {
            return 0f;
        }

        int bondedWingLevel = com.dragonspeech.enchant.EquippedEnchantments.levelOf(player, "vaengheill");
        float floorPercent = Math.max(0f, dragon.staminaLimiterPercent() - 5f * bondedWingLevel);

        float floor = dragon.maxDragonStamina() * (floorPercent / 100f);
        float available = Math.max(0f, dragon.dragonStamina() - floor);
        float toDraw = Math.min(remaining, available);
        if (toDraw <= 0f) {
            return 0f;
        }
        return dragon.feedStaminaTo(player, toDraw) > 0f ? toDraw : 0f;
    }
}
