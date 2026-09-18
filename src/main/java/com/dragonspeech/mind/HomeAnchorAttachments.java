package com.dragonspeech.mind;

import com.dragonspeech.DragonSpeech;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.server.level.ServerPlayer;

/**
 * Persistent storage for "heimbinda"'s home anchor - survives death
 * (copyOnDeath) and logout/login (persistent), same pattern as
 * PlayerMagicAttachments. A place you bound yourself to doesn't stop
 * being bound just because you died once.
 */
public final class HomeAnchorAttachments {

    public static final AttachmentType<HomeAnchor> HOME_ANCHOR = AttachmentRegistry.<HomeAnchor>builder()
        .copyOnDeath()
        .persistent(HomeAnchor.CODEC)
        .initializer(HomeAnchor::none)
        .buildAndRegister(DragonSpeech.id("home_anchor"));

    private HomeAnchorAttachments() {}

    /** Call from onInitialize() to force registration, like the other attachment classes. */
    public static void bootstrap() {}

    public static HomeAnchor get(ServerPlayer player) {
        HomeAnchor anchor = player.getAttached(HOME_ANCHOR);
        return anchor != null ? anchor : HomeAnchor.none();
    }

    public static void set(ServerPlayer player, HomeAnchor anchor) {
        player.setAttached(HOME_ANCHOR, anchor);
    }
}
