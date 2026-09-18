package com.dragonspeech.accessory;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * A real vanilla Container backing the 4 accessory slots - this is what
 * lets them plug into a real Slot in InventoryMenu (see the mixin) and
 * get vanilla's own proven click/drag/shift-click/network-sync handling
 * for free, rather than reimplementing any of that by hand.
 *
 * Loads its working copy from AccessorySlotsAccess ONCE at construction
 * and writes back on every mutation - safe because this container is
 * only ever constructed once per player session (see the InventoryMenu
 * mixin, which creates it inside the menu's own constructor, and that
 * menu lives exactly as long as the player's session does).
 */
public class AccessoryContainer implements Container {

    public static final int SLOT_COUNT = AccessorySlotsAttachments.SLOT_COUNT;

    private final ServerPlayer player;
    private final List<ItemStack> cache;

    public AccessoryContainer(ServerPlayer player) {
        this.player = player;
        this.cache = AccessorySlotsAccess.get(player);
    }

    @Override
    public int getContainerSize() {
        return SLOT_COUNT;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : cache) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return slot >= 0 && slot < SLOT_COUNT ? cache.get(slot) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        if (slot < 0 || slot >= SLOT_COUNT) {
            return ItemStack.EMPTY;
        }
        ItemStack full = cache.get(slot);
        if (full.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack split = full.split(amount);
        cache.set(slot, full);
        persist();
        return split;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        if (slot < 0 || slot >= SLOT_COUNT) {
            return ItemStack.EMPTY;
        }
        ItemStack full = cache.get(slot);
        cache.set(slot, ItemStack.EMPTY);
        persist();
        return full;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= SLOT_COUNT) {
            return;
        }
        cache.set(slot, stack);
        persist();
    }

    @Override
    public void setChanged() {
        persist();
    }

    @Override
    public boolean stillValid(Player player) {
        return player == this.player && player.isAlive();
    }

    @Override
    public void clearContent() {
        for (int i = 0; i < SLOT_COUNT; i++) {
            cache.set(i, ItemStack.EMPTY);
        }
        persist();
    }

    private void persist() {
        AccessorySlotsAccess.set(player, cache);
    }
}
