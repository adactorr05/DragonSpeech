package com.dragonspeech.mind;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;

import java.util.UUID;

/**
 * What one player has learned about ANOTHER entity's true name.
 * generationLearned is checked against the target's CURRENT TrueName
 * generation at the moment of use (see TrueNameService.knows()) - if the
 * target's name has since changed (personality shift, future hook), an
 * old learned name silently stops working instead of granting stale
 * domination forever. This is what makes "your true name can change"
 * (design notes) an actual counter-play instead of flavor text.
 */
public record LearnedTrueName(UUID targetId, String hashedPlaintext, int generationLearned) {

    public static final Codec<LearnedTrueName> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        UUIDUtil.CODEC.fieldOf("target_id").forGetter(LearnedTrueName::targetId),
        Codec.STRING.fieldOf("hashed_plaintext").forGetter(LearnedTrueName::hashedPlaintext),
        Codec.INT.fieldOf("generation_learned").forGetter(LearnedTrueName::generationLearned)
    ).apply(instance, LearnedTrueName::new));
}
