package com.dragonspeech.dragon.egg.habitats;

import com.dragonspeech.dragon.egg.Habitat;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Ported from Dragon Mounts Legacy (GPL-3.0). "below=true" scores
 * points for being below the given height AND unable to see the sky
 * (a cave/underground check, not just "low Y"); "below=false" scores
 * for being above the given height with no sky requirement. This is
 * what Gold Dragon's "hatches higher up, like on a mountain" habitat
 * uses (below=false, a tall height threshold).
 */
public record HeightHabitat(int points, boolean below, int height) implements Habitat {
    public static final String TYPE = "world_height";
    public static final MapCodec<HeightHabitat> CODEC = RecordCodecBuilder.mapCodec(func -> func.group(
            Habitat.withPoints(3, HeightHabitat::points),
            Codec.BOOL.optionalFieldOf("below", false).forGetter(HeightHabitat::below),
            Codec.INT.fieldOf("height").forGetter(HeightHabitat::height)
    ).apply(func, HeightHabitat::new));

    static {
        Habitat.register(TYPE, CODEC);
    }

    @Override
    public int getHabitatPoints(Level level, BlockPos pos) {
        int y = pos.getY();
        int max = height;
        return (below ? (y < max && !level.canSeeSky(pos)) : y > max) ? points : 0;
    }

    @Override
    public String typeId() {
        return TYPE;
    }
}
