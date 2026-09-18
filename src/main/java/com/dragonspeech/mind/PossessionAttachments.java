package com.dragonspeech.mind;

import com.dragonspeech.DragonSpeech;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;

/**
 * The crash-proofing for possession's inventory swap. EBW stashed the
 * possessor's inventory in raw entity NBT; here it's a PERSISTENT Fabric
 * data attachment, so the set-aside inventory survives a server crash or
 * restart mid-possession. On the next login, restoreIfOrphaned() sees a
 * stashed inventory with no live possession behind it and hands
 * everything back - a possessing player can never lose their items to a
 * badly-timed stop, which is the one genuinely dangerous failure mode of
 * this whole feature.
 *
 * Same VERSION-RISK caveat as PlayerMagicAttachments (this mirrors its
 * exact builder shape): if the attachment builder methods moved in your
 * fabric-api, fix it the same way there and here.
 */
public final class PossessionAttachments {

    /** CompoundTag wrapper holding the "inventory" ListTag; empty tag = nothing stashed. */
    public static final AttachmentType<CompoundTag> SET_ASIDE_INVENTORY = AttachmentRegistry.<CompoundTag>builder()
        .copyOnDeath()
        .persistent(Codec.PASSTHROUGH.xmap(
            dynamic -> {
                Tag tag = (Tag) dynamic.convert(net.minecraft.nbt.NbtOps.INSTANCE).getValue();
                return tag instanceof CompoundTag compound ? compound : new CompoundTag();
            },
            tag -> new com.mojang.serialization.Dynamic<>(net.minecraft.nbt.NbtOps.INSTANCE, tag)
        ))
        .initializer(CompoundTag::new)
        .buildAndRegister(DragonSpeech.id("possession_inventory"));

    private PossessionAttachments() {}

    /** Call from onInitialize() to force registration, like the other attachment classes. */
    public static void bootstrap() {
        // Intentionally empty - referencing the class triggers the static initializer.
    }

    static void saveInventory(ServerPlayer player) {
        player.setAttached(SET_ASIDE_INVENTORY, PossessionService.wrap(PossessionService.writeInventory(player)));
    }

    static void restoreInventory(ServerPlayer player) {
        CompoundTag stashed = player.getAttachedOrCreate(SET_ASIDE_INVENTORY);
        if (stashed.contains("inventory")) {
            PossessionService.readInventory(player, stashed.getList("inventory", Tag.TAG_COMPOUND));
        }
        player.setAttached(SET_ASIDE_INVENTORY, new CompoundTag());
    }

    /**
     * Login safety net: a stashed inventory with no live possession means
     * the server stopped mid-possession - give everything back.
     */
    public static void restoreIfOrphaned(ServerPlayer player) {
        if (PossessionService.isPossessing(player)) {
            return; // can't happen on a fresh join, but cheap to be exact
        }
        CompoundTag stashed = player.getAttachedOrCreate(SET_ASIDE_INVENTORY);
        if (stashed.contains("inventory")) {
            PossessionService.readInventory(player, stashed.getList("inventory", Tag.TAG_COMPOUND));
            player.setAttached(SET_ASIDE_INVENTORY, new CompoundTag());
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "The borrowed skin is long gone; your own things return to your hands."));
        }
    }
}
