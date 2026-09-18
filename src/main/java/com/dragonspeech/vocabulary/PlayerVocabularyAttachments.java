package com.dragonspeech.vocabulary;

import com.dragonspeech.DragonSpeech;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

/**
 * Same pattern and same version-risk as PlayerMagicAttachments (see that
 * file's comment for the full explanation and fallback). If you end up
 * fixing the builder shape there, apply the identical fix here.
 */
public final class PlayerVocabularyAttachments {

    private PlayerVocabularyAttachments() {}

    public static final AttachmentType<PlayerVocabulary> VOCABULARY = AttachmentRegistry.<PlayerVocabulary>builder()
        .copyOnDeath()
        .persistent(PlayerVocabulary.CODEC)
        .initializer(PlayerVocabulary::empty)
        .buildAndRegister(DragonSpeech.id("vocabulary"));

    public static void bootstrap() {
        // Intentionally empty - referencing the class is enough to trigger the static initializer above.
    }
}
