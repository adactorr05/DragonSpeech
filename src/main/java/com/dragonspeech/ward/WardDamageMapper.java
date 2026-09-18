package com.dragonspeech.ward;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;

import java.util.Optional;

/**
 * Maps a vanilla DamageSource to the WardType that should intercept it,
 * using vanilla's own damage-type tags wherever one exists - this means
 * modded damage that correctly tags itself (as well-behaved mods do)
 * gets warded correctly too, for free. Order matters: a flaming arrow is
 * checked as PROJECTILE before FIRE, an exploding fireball as EXPLOSION
 * first - the more specific delivery mechanism wins over the element.
 */
public final class WardDamageMapper {

    private WardDamageMapper() {}

    public static Optional<WardType> fromDamageSource(DamageSource source) {
        if (source.is(DamageTypeTags.IS_PROJECTILE)) {
            return Optional.of(WardType.PROJECTILE);
        }
        if (source.is(DamageTypeTags.IS_EXPLOSION)) {
            return Optional.of(WardType.EXPLOSION);
        }
        if (source.is(DamageTypeTags.IS_FALL)) {
            return Optional.of(WardType.FALL);
        }
        if (source.is(DamageTypeTags.IS_FIRE)) {
            return Optional.of(WardType.FIRE);
        }
        if (source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC)) {
            return Optional.of(WardType.MAGIC);
        }
        if (source.getDirectEntity() instanceof LivingEntity) {
            return Optional.of(WardType.MELEE);
        }
        return Optional.empty(); // starvation, drowning, void, etc. - not wardable
    }
}
