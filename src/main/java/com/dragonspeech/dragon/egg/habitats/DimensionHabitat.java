package com.dragonspeech.dragon.egg.habitats;

import com.dragonspeech.dragon.egg.Habitat;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * New habitat type, not in Dragon Mounts Legacy's original 7 - built
 * for Void Dragon's "anywhere in the End" and End Dragon's "in the
 * End" per explicit direction. Compares the actual level's dimension
 * key directly (e.g. minecraft:the_end, minecraft:the_nether) rather
 * than any block/biome proxy for "which dimension am I in."
 */
public record DimensionHabitat(int points, ResourceLocation dimension) implements Habitat {
    public static final String TYPE = "dimension";
    public static final MapCodec<DimensionHabitat> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Habitat.withPoints(5, DimensionHabitat::points),
            ResourceLocation.CODEC.fieldOf("dimension").forGetter(DimensionHabitat::dimension)
    ).apply(instance, DimensionHabitat::new));

    static {
        Habitat.register(TYPE, CODEC);
    }

    @Override
    public int getHabitatPoints(Level level, BlockPos pos) {
        ResourceKey<Level> here = level.dimension();
        return here.location().equals(dimension) ? points : 0;
    }

    @Override
    public String typeId() {
        return TYPE;
    }
}
