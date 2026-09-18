package com.dragonspeech.dragon.egg.habitats;

import com.dragonspeech.dragon.egg.Habitat;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

/** Ported from Dragon Mounts Legacy (GPL-3.0) - flat points if the egg's biome matches a tag. */
public record BiomeHabitat(int points, TagKey<Biome> biomeTag) implements Habitat {
    public static final String TYPE = "biome";
    public static final MapCodec<BiomeHabitat> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Habitat.withPoints(2, BiomeHabitat::points),
            TagKey.codec(Registries.BIOME).fieldOf("biome_tag").forGetter(BiomeHabitat::biomeTag)
    ).apply(instance, BiomeHabitat::new));

    static {
        Habitat.register(TYPE, CODEC);
    }

    @Override
    public int getHabitatPoints(Level level, BlockPos pos) {
        return level.getBiome(pos).is(biomeTag) ? points : 0;
    }

    @Override
    public String typeId() {
        return TYPE;
    }
}
