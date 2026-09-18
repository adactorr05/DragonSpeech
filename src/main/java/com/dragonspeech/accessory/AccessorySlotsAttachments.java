package com.dragonspeech.accessory;

import com.dragonspeech.DragonSpeech;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Persistent storage for the 4 accessory slots (necklaces/rings/gem
 * belts) - same registration shape as PlayerMagicAttachments/
 * EntityMarkAttachments (see PlayerMagicAttachments's own note for the
 * general version-risk that applies to every attachment in this
 * project). Deliberately NOT synced to the client via this attachment
 * directly (no .syncWith(...)) - the actual live contents reach the
 * client through the normal vanilla Slot/menu synchronization once
 * these are wired into a real Slot (see AccessoryContainer and the
 * InventoryMenu mixin), the same proven path every other inventory slot
 * already uses. This attachment only needs to survive save/load, not be
 * independently pushed over the network.
 */
public final class AccessorySlotsAttachments {

    public static final int SLOT_COUNT = 4;

    private AccessorySlotsAttachments() {}

    /**
     * FIXED: this was missing .copyOnDeath() entirely - meaning the
     * WHOLE attachment (all 4 slots) got wiped on respawn regardless of
     * what AccessoryDeathDrops decided to keep. Normal items never
     * noticed, since they get correctly DROPPED into the world as real
     * item entities before the wipe happens either way - but a
     * Curse of the Grave item, which AccessoryDeathDrops deliberately
     * leaves in place (never dropped, never cleared), still got lost
     * anyway once the attachment itself reset on the new player
     * instance. copyOnDeath() carries the attachment's actual data
     * forward across the death/respawn entity swap, which is what
     * "survives death" actually requires at the attachment level, not
     * just at the drop-logic level.
     */
    public static final AttachmentType<List<ItemStack>> SLOTS = AttachmentRegistry.<List<ItemStack>>builder()
        .persistent(ItemStack.OPTIONAL_CODEC.listOf())
        .copyOnDeath()
        .buildAndRegister(DragonSpeech.id("accessory_slots"));

    public static List<ItemStack> defaultEmpty() {
        return List.of(ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY);
    }

    /** Call from onInitialize() to force this class's static fields (and therefore registration) to run. */
    public static void bootstrap() {
        // Intentionally empty - referencing the class is enough to trigger the static initializer above.
    }
}
