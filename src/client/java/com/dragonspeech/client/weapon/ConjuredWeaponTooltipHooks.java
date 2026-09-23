package com.dragonspeech.client.weapon;

import com.dragonspeech.engine.MagicAffinity;
import com.dragonspeech.spell.SustainMode;
import com.dragonspeech.weapon.ConjuredWeaponItems;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Tooltip for persistent holographic conjured weapon/tool stacks. */
public final class ConjuredWeaponTooltipHooks {
    private ConjuredWeaponTooltipHooks() {}

    public static void register() {
        ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
            if (!ConjuredWeaponItems.isTemporary(stack)) return;

            lines.add(Component.literal("Conjured form").withStyle(ChatFormatting.AQUA));
            String form = ConjuredWeaponItems.toolType(stack).map(t -> pretty(t.getSerializedName())).orElse("Weapon");
            String material = ConjuredWeaponItems.material(stack).map(m -> pretty(m.getSerializedName())).orElse("Unknown");
            if (ConjuredWeaponItems.isHolographic(stack)) {
                String substance = ConjuredWeaponItems.affinity(stack)
                    .filter(affinity -> affinity != MagicAffinity.ARCANE)
                    .map(affinity -> pretty(affinity.getSerializedName()))
                    .orElse("Pure Magic");
                lines.add(Component.literal("Form: " + form + " · Substance: " + substance).withStyle(ChatFormatting.GRAY));
            } else {
                lines.add(Component.literal("Form: " + form + " · Material: " + material).withStyle(ChatFormatting.GRAY));
            }

            SustainMode mode = ConjuredWeaponItems.sustainMode(stack);
            var level = Minecraft.getInstance().level;
            switch (mode) {
                case DURATION -> {
                    if (ConjuredWeaponItems.durationFrozen(stack)) {
                        long remaining = ConjuredWeaponItems.frozenRemaining(stack);
                        lines.add(Component.literal("Duration frozen · " + ConjuredWeaponItems.durationText((int)Math.min(Integer.MAX_VALUE, remaining)) + " preserved")
                            .withStyle(ChatFormatting.LIGHT_PURPLE));
                    } else if (level != null) {
                        long remaining = Math.max(0L, ConjuredWeaponItems.expiresAt(stack) - level.getGameTime());
                        lines.add(Component.literal("Duration · unravels in " + ConjuredWeaponItems.durationText((int)Math.min(Integer.MAX_VALUE, remaining)))
                            .withStyle(remaining <= 20L * 10L ? ChatFormatting.RED : ChatFormatting.DARK_GRAY));
                    } else {
                        lines.add(Component.literal("Duration-bound construct").withStyle(ChatFormatting.DARK_GRAY));
                    }
                }
                case RESERVE -> lines.add(Component.literal("afla reserve · " + Math.round(ConjuredWeaponItems.reserve(stack))
                        + "/" + Math.round(ConjuredWeaponItems.maxReserve(stack)))
                    .withStyle(ChatFormatting.GOLD));
                case CASTER -> lines.add(Component.literal("aflbinda · sustained by its caster's stamina")
                    .withStyle(ChatFormatting.GREEN));
            }
        });
    }

    private static String pretty(String value) {
        if (value == null || value.isBlank()) return "Arcane";
        String lower = value.toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
