package com.dragonspeech.scar;

import com.dragonspeech.DragonSpeech;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.server.level.ServerPlayer;

/** Scars are lasting harm - they MUST survive death and respawn to mean anything, so this is one of the copyOnDeath attachments (unlike wards and wounds). */
public final class ScarAccess {

    private static final AttachmentType<PlayerScars> SCARS = AttachmentRegistry.<PlayerScars>builder()
        .copyOnDeath()
        .persistent(PlayerScars.CODEC)
        .initializer(PlayerScars::none)
        .buildAndRegister(DragonSpeech.id("scars"));

    private ScarAccess() {}

    public static void bootstrap() {
    }

    public static PlayerScars get(ServerPlayer player) {
        PlayerScars data = player.getAttached(SCARS);
        return data != null ? data : PlayerScars.none();
    }

    public static void set(ServerPlayer player, PlayerScars scars) {
        player.setAttached(SCARS, scars);
    }
}
