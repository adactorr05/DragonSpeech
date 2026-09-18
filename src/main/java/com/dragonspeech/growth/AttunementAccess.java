package com.dragonspeech.growth;

import net.minecraft.server.level.ServerPlayer;

public final class AttunementAccess {

    private AttunementAccess() {}

    public static PlayerAttunementData get(ServerPlayer player) {
        PlayerAttunementData data = player.getAttached(PlayerAttunementAttachments.ATTUNEMENT);
        return data != null ? data : PlayerAttunementData.empty();
    }

    public static void set(ServerPlayer player, PlayerAttunementData data) {
        player.setAttached(PlayerAttunementAttachments.ATTUNEMENT, data);
    }
}
