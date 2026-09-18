package com.dragonspeech.storage;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Stamina stored in an item belongs to the ITEM, not to whoever charged
 * it - anyone who has both the item and the gather-stamina skill can draw
 * on it, exactly like a real battery. This is what makes stamina-storage
 * items lootable/tradeable/stealable in a way that matters.
 */
public final class ItemStaminaStorage {

    private ItemStaminaStorage() {}

    public static float storedIn(ItemStack stack) {
        if (stack.isEmpty() || !StorageMediumRegistry.isValidMedium(stack.getItem())) {
            return 0f;
        }
        Float stored = stack.get(DragonSpeechComponents.STORED_STAMINA);
        return stored != null ? stored : 0f;
    }

    /** Energy available in the 4 equipped accessory slots ONLY - always considered, no word needed (that's the whole point of wearing them). */
    public static float availableAccessoryEnergy(ServerPlayer player) {
        float total = 0f;
        for (ItemStack stack : com.dragonspeech.accessory.AccessorySlotsAccess.get(player)) {
            total += storedIn(stack);
        }
        return total;
    }

    /** Energy available in ordinary (non-accessory) inventory items - only relevant when "telja" is spoken; see DrainResolver. */
    public static float availableGeneralInventoryEnergy(ServerPlayer player) {
        float total = 0f;
        for (ItemStack stack : player.getInventory().items) {
            total += storedIn(stack);
        }
        return total;
    }

    /**
     * Drains up to `amount` energy from the player's equipped accessory
     * slots ONLY. Always active - no word required, since the entire
     * point of the accessory slots is that a charged gem worn there just
     * works. Returns how much was actually drained.
     */
    public static float drainAccessories(ServerPlayer player, float amount) {
        float remaining = amount;
        java.util.List<ItemStack> accessories = com.dragonspeech.accessory.AccessorySlotsAccess.get(player);
        boolean changed = false;
        for (int i = 0; i < accessories.size() && remaining > 0f; i++) {
            ItemStack stack = accessories.get(i);
            float stored = storedIn(stack);
            if (stored <= 0f) {
                continue;
            }
            float used = Math.min(stored, remaining);
            stack.set(DragonSpeechComponents.STORED_STAMINA, stored - used);
            remaining -= used;
            changed = true;
        }
        if (changed) {
            com.dragonspeech.accessory.AccessorySlotsAccess.set(player, accessories);
        }
        return amount - remaining;
    }

    /**
     * Drains up to `amount` energy from ORDINARY inventory items,
     * first-slot-first - deliberately NOT part of the automatic drain
     * cascade anymore. A charged gem sitting loose in your pack does
     * nothing for a cast unless that cast speaks "telja" - equip it in
     * an accessory slot (see drainAccessories) if you want it to just
     * always work instead. Returns how much was actually drained.
     */
    public static float drainGeneralInventory(ServerPlayer player, float amount) {
        float remaining = amount;
        for (ItemStack stack : player.getInventory().items) {
            if (remaining <= 0f) {
                break;
            }
            float stored = storedIn(stack);
            if (stored <= 0f) {
                continue;
            }
            float used = Math.min(stored, remaining);
            stack.set(DragonSpeechComponents.STORED_STAMINA, stored - used);
            remaining -= used;
        }
        return amount - remaining;
    }

    /** Adds energy to a specific item stack, capped by its medium's max capacity. Returns how much was actually added (may be less than requested). */
    public static float charge(ItemStack stack, float amount) {
        if (!StorageMediumRegistry.isValidMedium(stack.getItem())) {
            return 0f;
        }
        StorageMediumRegistry.MediumProperties props = StorageMediumRegistry.propertiesOf(stack.getItem()).orElseThrow();
        float current = storedIn(stack);
        float newValue = Math.min(props.maxCapacity(), current + amount);
        float actuallyAdded = newValue - current;
        stack.set(DragonSpeechComponents.STORED_STAMINA, newValue);
        return actuallyAdded;
    }
}
