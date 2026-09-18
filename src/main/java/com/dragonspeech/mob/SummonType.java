package com.dragonspeech.mob;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

import java.util.Locale;

/**
 * Which creature a summon-noun word (beinvaettr, holdvaettr, ...) calls.
 * Mirrors WoundType's role exactly: a fixed, compiled enum that a NOUN
 * word can select via data, but can never invent a new entry for -
 * summon-able creatures are as hard-capped as effect handlers themselves.
 *
 * costMultiplier is a balance knob, not lore: common, low-danger
 * creatures (Skeleton, Zombie) sit at 1.0x; a genuine elemental threat
 * (Blaze) costs more to call. See SummonEffectHandler for how this
 * combines with the shared resurrection-relative base cost.
 */
public enum SummonType implements StringRepresentable {
    SKELETON(1.0f),
    ZOMBIE(1.0f),
    SPIDER(1.15f),
    WOLF(1.15f),
    ENDERMAN(1.5f),
    BLAZE(1.75f);

    public static final Codec<SummonType> CODEC = StringRepresentable.fromEnum(SummonType::values);

    private final float costMultiplier;

    SummonType(float costMultiplier) {
        this.costMultiplier = costMultiplier;
    }

    public float costMultiplier() {
        return costMultiplier;
    }

    public Entity create(Level level) {
        return switch (this) {
            case SKELETON -> EntityType.SKELETON.create(level);
            case ZOMBIE -> EntityType.ZOMBIE.create(level);
            case SPIDER -> EntityType.SPIDER.create(level);
            case WOLF -> EntityType.WOLF.create(level);
            case ENDERMAN -> EntityType.ENDERMAN.create(level);
            case BLAZE -> EntityType.BLAZE.create(level);
        };
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
