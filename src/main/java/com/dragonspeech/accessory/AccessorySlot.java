package com.dragonspeech.accessory;

import com.dragonspeech.enchant.EnchantmentKind;
import com.dragonspeech.enchant.MagicEnchantments;
import com.dragonspeech.storage.DragonSpeechComponents;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * One of the 4 accessory slots. Deliberately PERMISSIVE right now
 * (accepts any item) - this is the foundation piece; real necklace/
 * ring/gem-belt items with their own accessory-only restriction are a
 * follow-up layered on top of this once the slots themselves are
 * confirmed working. Restricting mayPlace() to a proper "is this an
 * accessory" check (an item tag, most likely) is the natural next step.
 */
public class AccessorySlot extends Slot {

    public AccessorySlot(Container container, int index, int x, int y) {
        super(container, index, x, y);
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return true;
    }

    @Override
    public int getMaxStackSize() {
        return 1; // jewelry is worn one-at-a-time per slot, not stacked
    }

    /**
     * Curse of the Grave ("haugbol") - "can never be taken off." This is
     * the actual enforcement of that: refuses the pickup outright if the
     * item in this slot carries an active haugbol curse, the same way
     * vanilla's own Curse of Binding refuses armor removal. Combined
     * with AccessoryDeathDrops (which separately skips dropping a
     * haugbol-cursed item on death), the item genuinely never leaves
     * this slot short of the stack being destroyed some other way
     * entirely (fire, void, /clear, etc. - none of those go through
     * this method at all, so this can't claim to be absolute).
     */
    @Override
    public boolean mayPickup(Player player) {
        ItemStack stack = getItem();
        if (!stack.isEmpty()) {
            MagicEnchantments enchantments = stack.getOrDefault(DragonSpeechComponents.MAGIC_ENCHANTMENTS, MagicEnchantments.EMPTY);
            boolean bound = enchantments.entries().stream()
                .anyMatch(e -> e.kind() == EnchantmentKind.CURSE && "haugbol".equals(e.wordId()));
            if (bound) {
                return false;
            }
        }
        return super.mayPickup(player);
    }
}
