package com.dragonspeech.mind;

import com.dragonspeech.DragonSpeech;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.server.level.ServerPlayer;

/** Same pattern and same version-risk as the other attachments - see PlayerMagicAttachments for the full explanation and fallback. */
public final class MindDataAccess {

    private static final AttachmentType<PlayerMindData> MIND_DATA = AttachmentRegistry.<PlayerMindData>builder()
        .persistent(PlayerMindData.CODEC)
        .initializer(PlayerMindData::empty)
        .buildAndRegister(DragonSpeech.id("mind_data"));

    private MindDataAccess() {}

    public static void bootstrap() {
        // Intentionally empty - referencing the class is enough to trigger the static initializer above.
    }

    public static PlayerMindData get(ServerPlayer player) {
        PlayerMindData data = player.getAttached(MIND_DATA);
        return data != null ? data : PlayerMindData.empty();
    }

    public static void set(ServerPlayer player, PlayerMindData data) {
        player.setAttached(MIND_DATA, data);
    }
}
