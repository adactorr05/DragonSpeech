package com.dragonspeech.block;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Locale;

/**
 * Which vanilla block a block-material noun word (jord, steinn, ...)
 * designates. Same hard-capped-enum pattern as WoundType/SummonType: a
 * NOUN word can select one of these via data, but the set of actual
 * blocks reachable from the Ancient Language is fixed in compiled code
 * and can never grow from a datapack alone.
 */
public enum BlockType implements StringRepresentable {
    DIRT,
    STONE,
    WOOD,
    SAND,
    NETHERRACK,
    END_STONE;

    public static final Codec<BlockType> CODEC = StringRepresentable.fromEnum(BlockType::values);

    /** "Wood" maps to a log rather than plank/other wood variants - the noun names the living-tree material, not a crafted form of it. */
    public BlockState blockState() {
        return switch (this) {
            case DIRT -> Blocks.DIRT.defaultBlockState();
            case STONE -> Blocks.STONE.defaultBlockState();
            case WOOD -> Blocks.OAK_LOG.defaultBlockState();
            case SAND -> Blocks.SAND.defaultBlockState();
            case NETHERRACK -> Blocks.NETHERRACK.defaultBlockState();
            case END_STONE -> Blocks.END_STONE.defaultBlockState();
        };
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
