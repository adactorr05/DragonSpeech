package com.dragonspeech.enchant;

import com.dragonspeech.storage.DragonSpeechComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * An item that can carry ward enchantments and show a durability-style
 * bar for them - deliberately NOT using vanilla's real Damage/MaxDamage
 * item components at all. Those drive vanilla's own "item breaks when
 * it reaches max damage" logic directly; since a durability-linked ward
 * must NEVER actually destroy the item (per spec: "shows as empty and
 * no longer works" at 0, not gone), the safest way to guarantee that is
 * to never touch the real damage system in the first place. Instead
 * this overrides the three bar-display hooks directly, computing
 * everything from MagicEnchantments (this mod's own data) - the bar
 * LOOKS like a normal durability bar (same visual language players
 * already know) but has nothing to do with vanilla's breakage mechanic
 * underneath.
 *
 * Shows the LOWEST current-durability-fraction among all
 * DURABILITY-sourced wards on the item, if any are present - if there
 * are none (no wards, or all wards on this item are stamina-linked
 * instead), the bar simply doesn't render, same as a normal item with
 * full durability.
 *
 * Color matches the stamina bar rather than vanilla's usual green-to-red
 * durability gradient, per spec ("same color as the stamina bar") -
 * using a flat color here rather than vanilla's fraction-based color
 * shift specifically because the point is visual association with
 * stamina, not a damage-style red warning.
 */
public class MagicEnchantableItem extends Item {

    /** Matches the stamina bar's own accent color used elsewhere in this mod's UI (the gold-ish "8a6a3b"/amber family already used for stamina-related particle effects). */
    private static final int STAMINA_BAR_COLOR = 0xFFD966;

    public MagicEnchantableItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return lowestDurabilityFraction(stack) < 1.0f;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0f * Math.max(0f, lowestDurabilityFraction(stack)));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return STAMINA_BAR_COLOR;
    }

    /** 1.0 if there are no durability-sourced wards at all (bar hidden), otherwise the lowest current/max fraction among them - so if ANY ward on the item is running low, the bar reflects that, not an average that could hide it. */
    private static float lowestDurabilityFraction(ItemStack stack) {
        MagicEnchantments enchantments = stack.getOrDefault(DragonSpeechComponents.MAGIC_ENCHANTMENTS, MagicEnchantments.EMPTY);
        float lowest = 1.0f;
        boolean anyDurabilitySourced = false;
        for (MagicEnchantment e : enchantments.entries()) {
            if (e.kind() == EnchantmentKind.WARD && (e.powerSource() == WardPowerSource.RESERVE || e.powerSource() == WardPowerSource.DURABILITY) && e.durabilityMax() > 0f) {
                anyDurabilitySourced = true;
                float fraction = e.durabilityCurrent() / e.durabilityMax();
                lowest = Math.min(lowest, fraction);
            }
        }
        return anyDurabilitySourced ? lowest : 1.0f;
    }
}
