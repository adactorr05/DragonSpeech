package com.dragonspeech.stamina;

import com.dragonspeech.DragonSpeech;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

/**
 * Same registration shape as PlayerMagicAttachments (see that class's own
 * version-risk note - it applies equally here), attached to entities
 * generally rather than players specifically, same as EntityMarkAttachments.
 * No .initializer() on purpose - absence means "never drained yet, so
 * full," which MobStaminaAccess.get() below handles without needing a
 * stored default for every mob that's never been touched.
 */
public final class MobStaminaAttachments {

    private MobStaminaAttachments() {}

    public static final AttachmentType<MobStaminaReserve> RESERVE = AttachmentRegistry.<MobStaminaReserve>builder()
        .persistent(MobStaminaReserve.CODEC)
        .buildAndRegister(DragonSpeech.id("mob_stamina_reserve"));

    /** Call from onInitialize() to force this class's static fields (and therefore registration) to run. */
    public static void bootstrap() {
        // Intentionally empty - referencing the class is enough to trigger the static initializer above.
    }
}
