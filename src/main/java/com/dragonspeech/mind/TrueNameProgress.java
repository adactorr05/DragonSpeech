package com.dragonspeech.mind;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;

import java.util.UUID;

/**
 * ONE ENTRY on the "true name card" list this player has, for ONE
 * target - unlocked the moment they win a mind duel against that
 * target (see TrueNameDuelHooks), regardless of which mind skill they
 * have. From there, `collectedLetters` grows one character at a time
 * (see TrueNameLetterAttempt) as they cast mind-words from the guessing
 * screen, until they either give up or correctly assemble the whole
 * name.
 *
 * `collectedLetters` is a plain String, not a Set/count map - order is
 * COLLECTION order (not the name's own letter order, which is the whole
 * point: "scrambled"), and duplicates are represented literally by the
 * character appearing twice if you happened to collect the same letter
 * twice. A name like "unveilia" (two i's) genuinely needs you to have
 * gathered an 'i' twice before you could ever assemble it correctly -
 * that difficulty is real, not simulated, because this is exactly what
 * a correct assembly attempt has to work with.
 */
public record TrueNameProgress(UUID targetId, String collectedLetters, boolean solved) {

    public static final Codec<TrueNameProgress> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        UUIDUtil.CODEC.fieldOf("target_id").forGetter(TrueNameProgress::targetId),
        Codec.STRING.optionalFieldOf("collected_letters", "").forGetter(TrueNameProgress::collectedLetters),
        Codec.BOOL.optionalFieldOf("solved", false).forGetter(TrueNameProgress::solved)
    ).apply(instance, TrueNameProgress::new));

    public static TrueNameProgress unlocked(UUID targetId) {
        return new TrueNameProgress(targetId, "", false);
    }

    public TrueNameProgress withLetterAdded(char c) {
        return new TrueNameProgress(targetId, collectedLetters + c, solved);
    }

    public TrueNameProgress markSolved() {
        return new TrueNameProgress(targetId, collectedLetters, true);
    }
}
