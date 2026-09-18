package com.dragonspeech.dragon.egg.habitats;

import com.dragonspeech.dragon.egg.Habitat;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/** Ported from Dragon Mounts Legacy (GPL-3.0) - counts tagged blocks in the 3x3x3 area around the egg. */
public record NearbyBlocksHabitat(float multiplier, TagKey<Block> tag) implements Habitat {
    public static final String TYPE = "nearby_blocks";
    public static final MapCodec<NearbyBlocksHabitat> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Habitat.withMultiplier(0.5f, NearbyBlocksHabitat::multiplier),
            TagKey.codec(Registries.BLOCK).fieldOf("block_tag").forGetter(NearbyBlocksHabitat::tag)
    ).apply(instance, NearbyBlocksHabitat::new));

    static {
        Habitat.register(TYPE, CODEC);
    }

    @Override
    public int getHabitatPoints(Level level, BlockPos basePos) {
        return (int) (BlockPos.betweenClosedStream(basePos.offset(1, 1, 1), basePos.offset(-1, -1, -1))
                .filter(p -> level.getBlockState(p).is(tag))
                .count() * multiplier);
    }

    @Override
    public String typeId() {
        return TYPE;
    }
}
