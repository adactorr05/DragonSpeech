package com.dragonspeech.effect;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * What a spell effect can act on. Deliberately closed (sealed) to exactly
 * three kinds - a block position, an entity, or a free direction cast.
 * There is no "OfPlayerData", "OfServerConfig", "OfCommand" variant, and
 * there never should be; if a future feature seems to need one, that's a
 * sign it belongs in its own carefully-designed subsystem (like the Word
 * of Words admin-grant system), not as a new case bolted onto general
 * spellcasting.
 *
 * OfDirection is the "marklaust" case: the sentence explicitly released
 * itself from needing a bound target, so the working goes out from an
 * origin point along a direction instead. Only handlers that declare
 * TargetKind.DIRECTION in their caps ever see one.
 */
public sealed interface EffectTarget permits EffectTarget.OfBlock, EffectTarget.OfEntity, EffectTarget.OfDirection {
    record OfBlock(BlockPos pos) implements EffectTarget {}
    record OfEntity(Entity entity) implements EffectTarget {}
    /** A free cast (marklaust): from `origin`, along normalized `direction`. */
    record OfDirection(Vec3 origin, Vec3 direction) implements EffectTarget {}
}
