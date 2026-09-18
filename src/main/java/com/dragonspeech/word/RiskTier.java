package com.dragonspeech.word;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/**
 * How dangerous it is to guess at this word incorrectly. This is hidden
 * from the player before a correct guess - they should never be able to
 * see a word's RiskTier ahead of time, only feel it in the backlash.
 * The actual backlash-severity numbers per tier belong in the Phase 3
 * guessing/backlash resolver, not here - this is just the classification.
 */
public enum RiskTier implements StringRepresentable {
    TRIVIAL,
    MODERATE,
    SEVERE,
    CATASTROPHIC;

    public static final Codec<RiskTier> CODEC = StringRepresentable.fromEnum(RiskTier::values);

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
