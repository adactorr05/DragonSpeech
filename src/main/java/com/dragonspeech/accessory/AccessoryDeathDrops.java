package com.dragonspeech.accessory;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;

import java.util.ArrayList;
import java.util.List;

/**
 * FIXED: accessory items used to just vanish permanently on death - the
 * 4 slots are backed by a custom persistent attachment (see
 * AccessorySlotsAttachments), completely separate from the vanilla
 * Inventory that Minecraft's own death-drop logic already knows how to
 * empty out. Vanilla has no idea this second inventory exists, so it
 * never dropped it, and nothing else in the mod ever cleared it either -
 * the items just sat in the (now-dead, about-to-respawn-empty) attachment
 * forever, unrecoverable.
 *
 * Hooks ServerLivingEntityEvents.AFTER_DEATH - the same proven Fabric
 * event this project already uses elsewhere (TimeSlowDamageHooks,
 * PossessionService use sibling events on the same class) - and drops
 * each equipped accessory item into the world exactly like a vanilla
 * inventory slot would, then clears the slots. Respects keepInventory,
 * same as every other slot does.
 *
 * EXCEPTION - Curse of the Grave ("haugbol"): "can never be... lost to
 * death." An item carrying that curse is skipped here entirely - stays
 * in its slot, un-dropped, un-cleared, exactly as if it were keepInventory
 * for that one item specifically regardless of the actual gamerule.
 */
public final class AccessoryDeathDrops {

    private AccessoryDeathDrops() {}

    public static void bootstrap() {
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (!(entity instanceof ServerPlayer player)) {
                return;
            }
            if (player.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) {
                return; // matches vanilla's own rule for every other slot - nothing drops
            }

            List<ItemStack> accessories = AccessorySlotsAccess.get(player);
            List<ItemStack> updated = new ArrayList<>();
            boolean anyChanged = false;
            for (ItemStack stack : accessories) {
                if (stack.isEmpty()) {
                    updated.add(ItemStack.EMPTY);
                    continue;
                }
                if (isBoundToTheGrave(stack)) {
                    updated.add(stack); // stays equipped, exactly as cursed
                    continue;
                }
                player.drop(stack, true);
                updated.add(ItemStack.EMPTY);
                anyChanged = true;
            }
            if (anyChanged) {
                AccessorySlotsAccess.set(player, updated);
            }
        });
    }

    private static boolean isBoundToTheGrave(ItemStack stack) {
        var enchantments = stack.getOrDefault(com.dragonspeech.storage.DragonSpeechComponents.MAGIC_ENCHANTMENTS,
            com.dragonspeech.enchant.MagicEnchantments.EMPTY);
        return enchantments.entries().stream()
            .anyMatch(e -> e.kind() == com.dragonspeech.enchant.EnchantmentKind.CURSE && "haugbol".equals(e.wordId()));
    }
}
