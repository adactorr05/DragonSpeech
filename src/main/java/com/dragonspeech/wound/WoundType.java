package com.dragonspeech.wound;

import com.mojang.serialization.Codec;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import java.util.Locale;

/**
 * What KIND of hurt a body carries. Damage is remembered by its nature,
 * and mending must name that nature: burns are not broken bones, and the
 * old tongue will not pretend they are. GENERIC covers what has no
 * clean name (drowning, starving, magic) - plain unworded healing can
 * only ever touch that pool.
 */
public enum WoundType implements StringRepresentable {
    BURN,       // fire, lava
    BLAST,      // explosions
    PIERCING,   // arrows, tridents, projectiles
    BONE,       // falls, crushing
    GASH,       // melee blows, claws, blades
    GENERIC;    // everything without a clean name

    public static final Codec<WoundType> CODEC = StringRepresentable.fromEnum(WoundType::values);

    /** Same category logic as WardDamageMapper - delivery mechanism first, element second. */
    public static WoundType fromDamageSource(DamageSource source) {
        if (source.is(DamageTypeTags.IS_PROJECTILE)) {
            return PIERCING;
        }
        if (source.is(DamageTypeTags.IS_EXPLOSION)) {
            return BLAST;
        }
        if (source.is(DamageTypeTags.IS_FALL)) {
            return BONE;
        }
        if (source.is(DamageTypeTags.IS_FIRE)) {
            return BURN;
        }
        if (source.getDirectEntity() instanceof LivingEntity) {
            return GASH;
        }
        return GENERIC;
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
