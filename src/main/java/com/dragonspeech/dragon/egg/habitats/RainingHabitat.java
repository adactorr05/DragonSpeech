package com.dragonspeech.dragon.egg.habitats;

import com.dragonspeech.dragon.egg.Habitat;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * New habitat type, not in Dragon Mounts Legacy's original 7 - built
 * for Lightning Dragon's "hatches when raining" per explicit direction.
 * Uses isRainingAt(pos) rather than the level-wide isRaining() flag -
 * that variant also accounts for being under a roof/indoors (an egg
 * sheltered from the rain doesn't count), matching how a real weather
 * exposure check should behave.
 */
public record RainingHabitat(int points) implements Habitat {
    public static final String TYPE = "raining";
    public static final MapCodec<RainingHabitat> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Habitat.withPoints(2, RainingHabitat::points)
    ).apply(instance, RainingHabitat::new));

    static {
        Habitat.register(TYPE, CODEC);
    }

    @Override
    public int getHabitatPoints(Level level, BlockPos pos) {
        return level.isRainingAt(pos) ? points : 0;
    }

    @Override
    public String typeId() {
        return TYPE;
    }
}
