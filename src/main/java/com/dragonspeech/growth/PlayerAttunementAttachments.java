package com.dragonspeech.growth;

import com.dragonspeech.DragonSpeech;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

/** Same pattern and same version-risk as PlayerMagicAttachments/PlayerVocabularyAttachments - see PlayerMagicAttachments for the full explanation and fallback. */
public final class PlayerAttunementAttachments {

    private PlayerAttunementAttachments() {}

    public static final AttachmentType<PlayerAttunementData> ATTUNEMENT = AttachmentRegistry.<PlayerAttunementData>builder()
        .copyOnDeath()
        .persistent(PlayerAttunementData.CODEC)
        .initializer(PlayerAttunementData::empty)
        .buildAndRegister(DragonSpeech.id("attunement"));

    public static void bootstrap() {
        // Intentionally empty - referencing the class is enough to trigger the static initializer above.
    }
}
