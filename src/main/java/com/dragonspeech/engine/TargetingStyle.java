package com.dragonspeech.engine;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/**
 * How a working finds (or re-finds) its mark, carried by targeting
 * modifier words: leitbinda -> HOMING, kedjubinda -> CHAIN. Same
 * fixed-compiled-set boundary as Element and SpellShape.
 */
public enum TargetingStyle implements StringRepresentable {
    /** The working bends toward the nearest mark near its path (leitbinda). */
    HOMING,
    /** After striking, the working leaps to further marks (kedjubinda). */
    CHAIN,
    /** The path is deliberately bent/deflected from its ordinary line (sveigja). */
    REDIRECT;

    public static final Codec<TargetingStyle> CODEC = StringRepresentable.fromEnum(TargetingStyle::values);

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
