package com.dragonspeech.enchant;

import com.dragonspeech.accessory.AccessorySlotsAccess;
import com.dragonspeech.stamina.StaminaAccess;
import com.dragonspeech.storage.DragonSpeechComponents;
import com.dragonspeech.ward.WardType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Item-enchanted WARDS actually absorbing damage - this is the piece
 * Phase 1 deliberately left out (see ApplyWardEnchantEffectHandler's own
 * doc). Mirrors WardService.absorb()'s proven shape (the existing
 * lightweight verja-style ward system) closely on purpose: walk every
 * candidate ward in order, let each one eat as much of the remaining
 * damage as it can, keep going until either the damage is gone or
 * there's nothing left to absorb with.
 *
 * ONLY WARDS IN AN EQUIPPED ACCESSORY SLOT COUNT - a galdrverja-enchanted
 * ring sitting in your general inventory does nothing, per spec ("has to
 * be in accessory slot for ward to be active"). Checked by reading
 * AccessorySlotsAccess directly, not general inventory.
 *
 * TWO POWER SOURCES, TWO DIFFERENT DRAINS:
 *   DURABILITY     - spends the ward's OWN stored pool (durabilityCurrent
 *                     on the MagicEnchantment entry itself), 1 damage per
 *                     1 point, floor 0. The item is mutated and written
 *                     back to the accessory slot.
 *   STAMINA_LINKED - spends the WEARER's own stamina/hunger/health via
 *                     CasterStaminaCascade (respects the current Magic
 *                     Difficulty floor - deactivates once tapped out on
 *                     Normal/Easy, can genuinely kill on Hard). Nothing
 *                     on the item itself changes.
 *
 * PHASE 1 SCOPE: galdrverja is a generic proof-of-concept ward with no
 * damage-type filtering yet - it absorbs ANY damage LivingEntityDamageMixin
 * already treats as "wardable" (the same WardDamageMapper check the
 * existing verja system uses - unwardable sources like drowning/magic/
 * starving are skipped here too, for the same reasons). Future specific
 * ward words (fire-only, melee-only, etc.) will need an actual
 * damage-type field added to MagicEnchantment and checked here - not
 * done yet, since only one generic ward word exists so far.
 */
public final class MagicWardCombat {

    private MagicWardCombat() {}

    public static float absorb(ServerPlayer defender, WardType damageType, float incomingDamage) {
        List<ItemStack> accessories = AccessorySlotsAccess.get(defender);
        float remaining = incomingDamage;
        float totalAbsorbed = 0f;
        boolean anyChanged = false;
        boolean anyStaminaDrained = false;
        boolean anyDepleted = false;
        long now = defender.level().getGameTime();

        for (ItemStack stack : accessories) {
            if (remaining <= 0.0001f || stack.isEmpty()) continue;
            MagicEnchantments enchantments = stack.getOrDefault(DragonSpeechComponents.MAGIC_ENCHANTMENTS, MagicEnchantments.EMPTY);
            if (enchantments.isEmpty()) continue;

            List<MagicEnchantment> entries = new ArrayList<>(enchantments.entries());
            boolean stackChanged = false;

            for (int i = 0; i < entries.size() && remaining > 0.0001f; i++) {
                MagicEnchantment ward = entries.get(i);
                if (ward.kind() != EnchantmentKind.WARD || !ward.wardMatches(damageType) || !ward.isActive(now)) continue;

                switch (ward.powerSource()) {
                    case DURATION -> {
                        totalAbsorbed += remaining;
                        remaining = 0f;
                    }
                    case RESERVE, DURABILITY -> {
                        float portion = Math.min(ward.durabilityCurrent(), remaining);
                        if (portion <= 0f) continue;
                        MagicEnchantment updated = ward.withDurability(ward.durabilityCurrent() - portion);
                        entries.set(i, updated);
                        stackChanged = true;
                        remaining -= portion;
                        totalAbsorbed += portion;
                        if (updated.durabilityCurrent() <= 0f) anyDepleted = true;
                    }
                    case STAMINA_LINKED -> {
                        ServerPlayer caster = defender;
                        if (ward.boundCasterId() != null && !ward.boundCasterId().isBlank()) {
                            try {
                                ServerPlayer found = defender.getServer().getPlayerList().getPlayer(java.util.UUID.fromString(ward.boundCasterId()));
                                if (found == null) continue; // binding is dormant while its original caster is offline
                                caster = found;
                            } catch (IllegalArgumentException ignored) {}
                        }
                        var stamina = StaminaAccess.get(caster);
                        float portion = Math.min(stamina.stamina(), remaining);
                        if (portion > 0f) {
                            float after = stamina.stamina() - portion;
                            StaminaAccess.set(caster, stamina.withStamina(after));
                            remaining -= portion;
                            totalAbsorbed += portion;
                            anyStaminaDrained = true;
                            if (after <= 0.0001f) {
                                entries.remove(i--); // aflbinda ends with the caster's stamina; no hidden reserve remains
                                stackChanged = true;
                            }
                        } else {
                            entries.remove(i--);
                            stackChanged = true;
                        }
                    }
                }
            }

            if (stackChanged) {
                stack.set(DragonSpeechComponents.MAGIC_ENCHANTMENTS, new MagicEnchantments(entries));
                anyChanged = true;
            }
        }

        if (anyChanged) AccessorySlotsAccess.set(defender, accessories);

        if (anyStaminaDrained) {
            defender.sendSystemMessage(Component.literal("A bound ward holds, drawing only from its caster's stamina."));
        } else if (anyDepleted && remaining > 0.0001f) {
            defender.sendSystemMessage(Component.literal("An afla ward runs out of its stored reserve before the blow is spent."));
        } else if (anyDepleted) {
            defender.sendSystemMessage(Component.literal("An afla ward spends the last of its own reserve."));
        } else if (totalAbsorbed > 0f) {
            defender.sendSystemMessage(Component.literal("A ward bound into what you wear turns the blow aside."));
        }
        return totalAbsorbed;
    }

}
