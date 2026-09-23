package com.dragonspeech.word;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/**
 * How a word is intended to be found. This is metadata for loot tables,
 * NPC dialogue trees, and the grimoire's "where you learned this" display -
 * it does not prevent a word from also being found via guessing. Guessing
 * is always possible for any word regardless of its intended discovery
 * method; this field just drives the "normal" path content creators hook
 * their structures/NPCs up to.
 */
public enum DiscoveryMethod implements StringRepresentable {
    RUIN_TABLET,
    MENTOR_NPC,
    ANCIENT_TEXT,
    ELVEN_TRIAL,
    DANGER_WORD,
    ADMIN_GRANTED,
    GUESSED;

    public static final Codec<DiscoveryMethod> CODEC = StringRepresentable.fromEnum(DiscoveryMethod::values);

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
