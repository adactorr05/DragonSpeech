package com.dragonspeech.dragon.egg.habitats;

import com.dragonspeech.dragon.egg.Habitat;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * New habitat type, not in Dragon Mounts Legacy's original 7 - built
 * for "if lightning is striking, it will speed up its hatching" per
 * explicit direction. Same pattern as their own DragonBreathHabitat
 * (check for a specific, momentary entity nearby right now, not a
 * level-wide state flag) - a real LightningBolt entity only exists for
 * the handful of ticks an actual strike is happening, so this is a
 * genuine "lightning is striking near me RIGHT NOW" check, distinct
 * from RainingHabitat's broader "it's raining" state. A breed JSON
 * combining both (via AnyOfHabitat or just as two separate habitats
 * whose points both count, since DragonBreed.habitats is a plain list
 * that sums independently unless wrapped in Picky/AnyOf) is what
 * produces the "extra boost specifically during an actual strike" feel.
 */
public record NearbyLightningHabitat(int points, double radius) implements Habitat {
    public static final String TYPE = "nearby_lightning";
    public static final MapCodec<NearbyLightningHabitat> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Habitat.withPoints(5, NearbyLightningHabitat::points),
            com.mojang.serialization.Codec.DOUBLE.optionalFieldOf("radius", 8.0).forGetter(NearbyLightningHabitat::radius)
    ).apply(instance, NearbyLightningHabitat::new));

    static {
        Habitat.register(TYPE, CODEC);
    }

    @Override
    public int getHabitatPoints(Level level, BlockPos pos) {
        AABB area = new AABB(pos).inflate(radius);
        return !level.getEntitiesOfClass(LightningBolt.class, area).isEmpty() ? points : 0;
    }

    @Override
    public String typeId() {
        return TYPE;
    }
}
