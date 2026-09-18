package com.dragonspeech.stamina;

import net.minecraft.server.level.ServerPlayer;

/**
 * Thin wrapper around the attachment get/set calls. Every other file
 * touches PlayerMagicData through here, never through the attachment API
 * directly - if PlayerMagicAttachments needs a fix for your exact
 * fabric-api version, this is the only other file that might need a
 * matching small adjustment.
 */
public final class StaminaAccess {

    private StaminaAccess() {}

    public static PlayerMagicData get(ServerPlayer player) {
        PlayerMagicData data = player.getAttached(PlayerMagicAttachments.MAGIC_DATA);
        return data != null ? data : PlayerMagicData.initial();
    }

    public static void set(ServerPlayer player, PlayerMagicData data) {
        player.setAttached(PlayerMagicAttachments.MAGIC_DATA, data);
    }
}
