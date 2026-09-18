package com.dragonspeech.eldunari;

import com.dragonspeech.storage.DragonSpeechComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Renamed from EldunariAccess - see DragonHeartState's own doc for the
 * rename's reasoning.
 *
 * "If you have gained permission from the heart (or broken its mind)
 * you can use that stamina as your own." - the actual "use as your own"
 * half of that sentence. Mirrors ItemStaminaStorage.drain's shape
 * exactly, but only pulls from a usable() heart, and is called from
 * DrainResolver at TWO points (before/after the player's own stamina)
 * instead of one unconditional draw - see drainBefore/drainAfter below.
 *
 * Each heart carries its own HEART_USE_STAMINA/HEART_STAMINA_BEFORE_OWN
 * settings (default true/true - on, before - when absent, so a
 * freshly-broken heart is useful immediately without needing a trip to
 * its settings screen first).
 */
public final class DragonHeartAccess {

    private DragonHeartAccess() {}

    public static float drainBefore(ServerPlayer player, float amount) {
        return drain(player, amount, true);
    }

    public static float drainAfter(ServerPlayer player, float amount) {
        return drain(player, amount, false);
    }

    private static float drain(ServerPlayer player, float amount, boolean wantBefore) {
        float remaining = amount;
        for (ItemStack stack : player.getInventory().items) {
            if (remaining <= 0f) {
                break;
            }
            if (!isHeart(stack)) {
                continue;
            }
            String rawState = stack.get(DragonSpeechComponents.ELDUNARI_STATE);
            DragonHeartState state;
            try {
                state = rawState == null ? DragonHeartState.UNBONDED : DragonHeartState.valueOf(rawState);
            } catch (IllegalArgumentException e) {
                state = DragonHeartState.UNBONDED;
            }
            if (!state.usable()) {
                continue;
            }

            Boolean useStaminaBoxed = stack.get(DragonSpeechComponents.HEART_USE_STAMINA);
            boolean useStamina = useStaminaBoxed == null || useStaminaBoxed; // default on
            if (!useStamina) {
                continue;
            }
            Boolean beforeOwnBoxed = stack.get(DragonSpeechComponents.HEART_STAMINA_BEFORE_OWN);
            boolean beforeOwn = beforeOwnBoxed == null || beforeOwnBoxed; // default before
            if (beforeOwn != wantBefore) {
                continue; // this heart's setting doesn't match which tier is currently being drawn from
            }

            Float storedBoxed = stack.get(DragonSpeechComponents.ELDUNARI_ENERGY);
            float stored = storedBoxed != null ? storedBoxed : 0f;
            if (stored <= 0f) {
                continue;
            }
            float used = Math.min(stored, remaining);
            stack.set(DragonSpeechComponents.ELDUNARI_ENERGY, stored - used);
            remaining -= used;
        }
        return amount - remaining;
    }

    private static boolean isHeart(ItemStack stack) {
        return stack.getItem() instanceof DragonHeartItem;
    }
}
