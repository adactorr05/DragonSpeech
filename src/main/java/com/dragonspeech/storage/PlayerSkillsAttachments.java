package com.dragonspeech.storage;

import com.dragonspeech.DragonSpeech;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

/** Same pattern and same version-risk as the other attachments - see PlayerMagicAttachments for the full explanation and fallback. */
public final class PlayerSkillsAttachments {

    private PlayerSkillsAttachments() {}

    public static final AttachmentType<PlayerSkills> SKILLS = AttachmentRegistry.<PlayerSkills>builder()
        .copyOnDeath()
        .persistent(PlayerSkills.CODEC)
        .initializer(PlayerSkills::none)
        .buildAndRegister(DragonSpeech.id("skills"));

    public static void bootstrap() {
        // Intentionally empty - referencing the class is enough to trigger the static initializer above.
    }
}
