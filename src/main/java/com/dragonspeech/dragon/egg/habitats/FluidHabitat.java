package com.dragonspeech.dragon.egg.habitats;

import com.dragonspeech.dragon.egg.Habitat;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;

/** Ported from Dragon Mounts Legacy (GPL-3.0) - counts tagged fluid blocks in the 3x3x3 area around the egg. */
public record FluidHabitat(float multiplier, TagKey<Fluid> fluidType) implements Habitat {
    public static final String TYPE = "in_fluid";
    public static final MapCodec<FluidHabitat> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Habitat.withMultiplier(0.5f, FluidHabitat::multiplier),
            TagKey.codec(Registries.FLUID).fieldOf("fluid_tag").forGetter(FluidHabitat::fluidType)
    ).apply(instance, FluidHabitat::new));

    static {
        Habitat.register(TYPE, CODEC);
    }

    @Override
    public int getHabitatPoints(Level level, BlockPos pos) {
        return (int) (BlockPos.betweenClosedStream(pos.offset(1, 1, 1), pos.offset(-1, -1, -1))
                .filter(p -> level.getFluidState(p).is(fluidType))
                .count() * multiplier);
    }

    @Override
    public String typeId() {
        return TYPE;
    }
}
