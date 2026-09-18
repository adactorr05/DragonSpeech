package com.dragonspeech.wound;

import com.dragonspeech.DragonSpeech;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.server.level.ServerPlayer;

/** Same pattern as the other attachments - but deliberately WITHOUT copyOnDeath: a fresh body carries no old wounds. */
public final class WoundAccess {

    private static final AttachmentType<PlayerWounds> WOUNDS = AttachmentRegistry.<PlayerWounds>builder()
        .persistent(PlayerWounds.CODEC)
        .initializer(PlayerWounds::none)
        .buildAndRegister(DragonSpeech.id("wounds"));

    private WoundAccess() {}

    public static void bootstrap() {
        // Intentionally empty - referencing the class triggers the static initializer.
    }

    public static PlayerWounds get(ServerPlayer player) {
        PlayerWounds data = player.getAttached(WOUNDS);
        return data != null ? data : PlayerWounds.none();
    }

    public static void set(ServerPlayer player, PlayerWounds wounds) {
        player.setAttached(WOUNDS, wounds);
    }
}
