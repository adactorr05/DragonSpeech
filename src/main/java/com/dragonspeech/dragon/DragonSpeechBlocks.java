package com.dragonspeech.dragon;

import com.dragonspeech.DragonSpeech;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;

import java.util.EnumMap;
import java.util.Map;

/**
 * "I want it to be 7 eggs. 1 egg for each color" per explicit direction.
 * Registers all 7 VariantDragonEggBlocks and their DragonEggBlockItem
 * wrappers - the block-registration pattern here (Properties.
 * ofFullCopy(Blocks.DRAGON_EGG)) is recovered almost exactly from an
 * uploaded backup of an earlier ChatGPT-assisted pass at this mod,
 * which had already worked out this same approach for 6 of these 7
 * colors before this project moved to Claude.
 *
 * explosionResistance bumped past vanilla's own dragon egg value
 * (matches obsidian) on top of the copied properties - "is unbreakable"
 * per explicit direction covers normal mining (see VariantDragonEggBlock.
 * getDestroyProgress) and explosions both.
 */
public final class DragonSpeechBlocks {

    // FIX: this map MUST be declared (and therefore initialized) before
    // any of the 7 registerEgg() calls below - Java runs static field
    // initializers top-to-bottom in DECLARATION order, and registerEgg()
    // writes into this map. It was previously declared AFTER the 7
    // block fields, so the very first registerEgg() call tried to call
    // .put() on a map that hadn't been constructed yet - still null at
    // that point - causing the exact NPE in the crash ("Cannot invoke
    // java.util.Map.put(Object, Object) because BY_COLOR is null").
    // Classic Java static-init-order mistake, not a logic error in
    // registerEgg() itself.
    private static final Map<DragonColor, Block> BY_COLOR = new EnumMap<>(DragonColor.class);

    public static final Block RED_DRAGON_EGG = registerEgg("red_dragon_egg", DragonColor.RED);
    public static final Block BRONZE_DRAGON_EGG = registerEgg("bronze_dragon_egg", DragonColor.BRONZE);
    public static final Block BLUE_DRAGON_EGG = registerEgg("blue_dragon_egg", DragonColor.BLUE);
    public static final Block WHITE_DRAGON_EGG = registerEgg("white_dragon_egg", DragonColor.WHITE);
    public static final Block GREEN_DRAGON_EGG = registerEgg("green_dragon_egg", DragonColor.GREEN);
    public static final Block BLACK_DRAGON_EGG = registerEgg("black_dragon_egg", DragonColor.BLACK);
    public static final Block ENDER_DRAGON_EGG = registerEgg("ender_dragon_egg", DragonColor.ENDER);

    private DragonSpeechBlocks() {}

    /** Referencing the class is enough to trigger the static initializers above - same bootstrap pattern as every other registry class in this mod. */
    public static void bootstrap() {}

    public static Block byColor(DragonColor color) {
        return BY_COLOR.get(color);
    }

    /** All 7, in a stable (enum declaration) order - used by loot injection to pick one at random. */
    public static Block[] all() {
        return BY_COLOR.values().toArray(new Block[0]);
    }

    private static Block registerEgg(String id, DragonColor color) {
        Block block = Registry.register(
                BuiltInRegistries.BLOCK,
                DragonSpeech.id(id),
                new VariantDragonEggBlock(color, BlockBehaviour.Properties.ofFullCopy(Blocks.DRAGON_EGG).explosionResistance(1200f))
        );

        Registry.register(
                BuiltInRegistries.ITEM,
                DragonSpeech.id(id),
                new DragonEggBlockItem(block, new Item.Properties().stacksTo(1), color)
        );

        BY_COLOR.put(color, block);
        return block;
    }
}