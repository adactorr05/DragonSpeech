package com.dragonspeech.dragon;

import net.minecraft.server.level.ServerPlayer;

/** Thin wrapper around the attachment get/set calls - same pattern as StaminaAccess. Every other file touches PlayerBondData through here, never through the attachment API directly. */
public final class PlayerBondAccess {

    private PlayerBondAccess() {}

    public static PlayerBondData get(ServerPlayer player) {
        PlayerBondData data = player.getAttached(PlayerBondAttachments.BOND_DATA);
        return data != null ? data : PlayerBondData.initial();
    }

    public static void set(ServerPlayer player, PlayerBondData data) {
        player.setAttached(PlayerBondAttachments.BOND_DATA, data);
    }
}
