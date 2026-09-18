package com.dragonspeech.accessory;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Thin wrapper around the accessory-slots attachment, same role StaminaAccess/MarkRegistry play for their own data. */
public final class AccessorySlotsAccess {

    private AccessorySlotsAccess() {}

    /** A fresh, independently-mutable copy - callers mutate this and pass it back to set(), never the stored list directly. */
    public static List<ItemStack> get(ServerPlayer player) {
        List<ItemStack> stored = player.getAttached(AccessorySlotsAttachments.SLOTS);
        if (stored == null || stored.size() != AccessorySlotsAttachments.SLOT_COUNT) {
            return new ArrayList<>(AccessorySlotsAttachments.defaultEmpty());
        }
        return new ArrayList<>(stored);
    }

    public static void set(ServerPlayer player, List<ItemStack> slots) {
        player.setAttached(AccessorySlotsAttachments.SLOTS, List.copyOf(slots));
    }
}
