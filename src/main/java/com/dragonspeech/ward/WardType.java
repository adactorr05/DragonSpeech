package com.dragonspeech.ward;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/**
 * What a ward is keyed to intercept. Deliberately a fixed enum of broad
 * vanilla damage categories rather than an open-ended string - a ward's
 * wording picks one of these via which BINDING word was used, it can't
 * invent a new trigger type. This is the same "words select, they don't
 * invent" boundary EffectHandler enforces for active spells.
 */
public enum WardType implements StringRepresentable {
    PROJECTILE,
    EXPLOSION,
    FALL,
    MELEE,
    FIRE,
    MAGIC,
    /** Not a shield at all: a revival binding placed by "aftrlifga sjalfan" while alive. Consumed by DeathHooks the moment its caster dies - see ResurrectEffectHandler. Never returned by WardDamageMapper. */
    REVIVAL;

    public static final Codec<WardType> CODEC = StringRepresentable.fromEnum(WardType::values);

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
