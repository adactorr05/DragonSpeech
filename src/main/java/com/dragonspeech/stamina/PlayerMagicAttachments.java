package com.dragonspeech.stamina;

import com.dragonspeech.DragonSpeech;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

/**
 * Registers PlayerMagicData as a persistent Fabric data attachment, so it
 * survives world save/load and player relog without any manual NBT code.
 *
 * VERSION-RISK NOTE (highest in the project so far): Fabric's Data
 * Attachment API is one of the newer parts of fabric-api and its exact
 * builder shape has moved around release to release. What's written here
 * (AttachmentRegistry.builder() -> .persistent(codec) -> .buildAndRegister(id))
 * reflects the general v1 API shape. If this doesn't compile:
 *   1. Check net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry's
 *      actual methods in your IDE - method names/order may differ slightly.
 *   2. If the attachment API isn't available at all in your fabric-api
 *      version, the fallback is Cardinal Components API (a well-established
 *      third-party library predating this Fabric API feature) - same
 *      overall idea, different registration calls.
 * Either way, StaminaAccess is the ONLY other file that touches this
 * directly, so a fix here is a small, contained change.
 */
public final class PlayerMagicAttachments {

    private PlayerMagicAttachments() {}

    public static final AttachmentType<PlayerMagicData> MAGIC_DATA = AttachmentRegistry.<PlayerMagicData>builder()
        .copyOnDeath()
        .persistent(PlayerMagicData.CODEC)
        .initializer(PlayerMagicData::initial)
        .buildAndRegister(DragonSpeech.id("magic_data"));

    /** Call from onInitialize() to force this class's static fields (and therefore registration) to run. */
    public static void bootstrap() {
        // Intentionally empty - referencing the class is enough to trigger the static initializer above.
    }
}
