package com.dragonspeech.stamina;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * A NOTIONAL stamina pool for non-player living entities - mobs don't
 * have a real PlayerMagicData the way players do, but "aflsuga" draining
 * one repeatedly with no memory of how much it's already taken (the
 * original bug: every single cast treated the target as freshly full)
 * meant a mob could be drained infinitely, forever, with no real limit -
 * exactly the "no limit to how much it draws" problem. This gives mobs
 * a real, persistent, slowly-regenerating reserve instead, so repeated
 * draining actually exhausts something - see MobStaminaAccess for the
 * lazy regen math and DrainStaminaEffectHandler for where this spills
 * into real health damage once exhausted.
 *
 * `reserve` is the pool level AS OF `lastUpdateGameTime` - actual
 * current value needs the elapsed-time regen added back in, which is
 * why nothing reads this record's fields directly; go through
 * MobStaminaAccess instead.
 *
 * The max size is no longer a single flat constant here - see
 * MobStaminaScaling for the per-entity-type lookup ("there should be
 * differences in their stamina" per direction: a chicken shouldn't have
 * the same pool as a Shade). full(now) below still needs SOME size to
 * construct a record with, so it takes the max as a parameter now
 * instead of reading a constant.
 */
public record MobStaminaReserve(float reserve, long lastUpdateGameTime) {

    public static final Codec<MobStaminaReserve> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.FLOAT.fieldOf("reserve").forGetter(MobStaminaReserve::reserve),
        Codec.LONG.fieldOf("last_update").forGetter(MobStaminaReserve::lastUpdateGameTime)
    ).apply(instance, MobStaminaReserve::new));

    public static MobStaminaReserve full(float max, long now) {
        return new MobStaminaReserve(max, now);
    }
}
