package com.dragonspeech.worldgen;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

import java.util.Random;

/**
 * A small, procedurally-laid ring of weathered stone around a single
 * loot-bearing lectern-chest - a "Dragon Speech shrine." Rare and sparse
 * by design (see the placed_feature JSON's rarity_filter).
 *
 * SCOPED HONESTLY: this is a deliberate first slice, not the full
 * "custom ruins" vision. A proper multi-room jigsaw ruin (several
 * connected pieces, varied layouts) needs hand-authored NBT structure
 * templates - normally built in-game with structure blocks and exported
 * - which isn't something I can responsibly produce blind without a
 * testing loop. This procedural feature is genuinely in the world,
 * genuinely holds tablets, and genuinely required real worldgen
 * registration - it's just architecturally simpler than a jigsaw
 * structure. Expanding to real multi-piece ruins is a well-defined
 * follow-up once you can supply (or we can co-author in-game) actual
 * structure NBT files.
 *
 * VERSION-RISK NOTE: Feature<NoneFeatureConfiguration> and
 * FeaturePlaceContext are about as stable as worldgen code gets, but
 * RandomizableContainerBlockEntity's loot-table-setting method name has
 * shifted before (setLootTable vs setLootTable+seed overloads). Check
 * that call below against your decompiled sources if this fails to build.
 */
public class ShrineFeature extends Feature<NoneFeatureConfiguration> {

    private static final ResourceKey<net.minecraft.world.level.storage.loot.LootTable> LOOT_TABLE =
            ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.fromNamespaceAndPath("dragonspeech", "chests/ancient_shrine"));

    public ShrineFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        Random random = new Random(context.random().nextLong());

        // Find solid ground under the origin rather than trusting it exactly.
        BlockPos center = findGround(level, origin);
        if (center == null) {
            return false;
        }

        BlockState ring = Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
        BlockState cracked = Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
        int radius = 3;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist >= radius - 0.5 && dist <= radius + 0.5) {
                    BlockPos pos = center.offset(dx, 0, dz);
                    BlockPos ground = findGround(level, pos);
                    if (ground != null) {
                        level.setBlock(ground, random.nextFloat() < 0.3f ? cracked : ring, 3);
                    }
                }
            }
        }

        // The focal point: a single chest at the center, on a small stone dais.
        level.setBlock(center, Blocks.CHISELED_STONE_BRICKS.defaultBlockState(), 3);
        BlockPos chestPos = center.above();
        level.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), 3);

        if (level.getBlockEntity(chestPos) instanceof RandomizableContainerBlockEntity chest) {
            chest.setLootTable(LOOT_TABLE, random.nextLong());
        }

        return true;
    }

    private static BlockPos findGround(WorldGenLevel level, BlockPos near) {
        BlockPos.MutableBlockPos pos = near.mutable();
        for (int i = 0; i < 8; i++) {
            if (!level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir()) {
                return pos.immutable().above();
            }
            pos.move(0, -1, 0);
        }
        return null;
    }
}