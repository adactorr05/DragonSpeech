package com.dragonspeech.dragon.egg;

import com.dragonspeech.DragonSpeech;
import com.dragonspeech.dragon.DragonSpeechBlocks;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * No block entity existed anywhere in this project before now - this
 * is the standard Fabric BlockEntityType registration pattern, matched
 * to this project's own registry-class conventions (see
 * DragonSpeechEntities/DragonSpeechBlocks for the same shape).
 *
 * validBlocks is every one of the 7 VariantDragonEggBlocks
 * (DragonSpeechBlocks.all()) - a single BlockEntityType shared across
 * all 7 colors, same as how DragonEntity is one EntityType shared
 * across all 7 breeds via data, not 7 separate entity types.
 */
public final class DragonEggHatchingBlockEntities {

    // FIX: "self-reference in initializer" - the previous version
    // (last round) tried having HATCHING_EGG reference itself by name
    // inside its own initializer's lambda. I believed this was valid
    // because the lambda body is deferred execution, but the real
    // crash log proved that wrong - javac's definite-assignment
    // checker flags this regardless of when the lambda actually runs.
    // Real fix: a SEPARATE, non-final holder field the lambda
    // references instead - javac's check is specifically about a field
    // referencing ITSELF, not a different field, even one assigned
    // moments later from the same value.
    private static BlockEntityType<DragonEggHatchingBlockEntity> typeHolder;

    public static final BlockEntityType<DragonEggHatchingBlockEntity> HATCHING_EGG = Registry.register(
        BuiltInRegistries.BLOCK_ENTITY_TYPE,
        DragonSpeech.id("hatching_dragon_egg"),
        BlockEntityType.Builder.of((pos, state) -> new DragonEggHatchingBlockEntity(typeHolder, pos, state), DragonSpeechBlocks.all()).build(null)
    );

    static {
        typeHolder = HATCHING_EGG;
    }

    private DragonEggHatchingBlockEntities() {}

    /** Call once from onInitialize() - just needs to be referenced to run the static registration above. */
    public static void register() {}
}
