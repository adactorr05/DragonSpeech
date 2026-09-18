package com.dragonspeech.word;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/**
 * The grammatical role a word plays when placed into the casting grid.
 * This mirrors the Ancient Language's own internal grammar rather than
 * being a flat list of "spell types" - a spell is built by combining
 * words of different categories into a valid structure.
 */
public enum WordCategory implements StringRepresentable {
    /** An action: ignite, move, mend, sunder, reveal... */
    VERB,
    /** What the action targets: fire, stone, flesh, water, a mind... */
    NOUN_TARGET,
    /** How or how much: greatly, slightly, swiftly, permanently... */
    MODIFIER,
    /** Bounds the effect: "this", "all within reach", a specific true name... */
    SCOPE,
    /** Grammar particles that interrupt or end a spell, e.g. the universal stop-word. */
    CONTROL,
    /** Oath/persistent-effect words - enables wards and other binding spells. */
    BINDING;

    public static final Codec<WordCategory> CODEC = StringRepresentable.fromEnum(WordCategory::values);

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
