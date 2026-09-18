package com.dragonspeech.race;

import com.dragonspeech.DragonSpeech;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.server.level.ServerPlayer;

/** An origin is identity, not a temporary state - copyOnDeath, same reasoning as scars. */
public final class RaceAccess {

    private static final AttachmentType<PlayerRaceData> RACE = AttachmentRegistry.<PlayerRaceData>builder()
        .copyOnDeath()
        .persistent(PlayerRaceData.CODEC)
        .initializer(PlayerRaceData::unset)
        .buildAndRegister(DragonSpeech.id("race"));

    private RaceAccess() {}

    public static void bootstrap() {
    }

    public static PlayerRaceData get(ServerPlayer player) {
        PlayerRaceData data = player.getAttached(RACE);
        return data != null ? data : PlayerRaceData.unset();
    }

    public static void set(ServerPlayer player, PlayerRaceData data) {
        player.setAttached(RACE, data);
    }
}
