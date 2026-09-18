package com.dragonspeech.worldgen;

import com.dragonspeech.DragonSpeech;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Registers the FEATURE (code side only) - the actual instance (where it
 * spawns, how rare) is data-driven, in
 * data/dragonspeech/worldgen/configured_feature and .../placed_feature.
 * This is the standard vanilla split: code defines what a feature CAN
 * do, JSON configures a specific placement of it.
 */
public final class DragonSpeechFeatures {

    public static final Feature<NoneFeatureConfiguration> ANCIENT_SHRINE = Registry.register(
        BuiltInRegistries.FEATURE,
        DragonSpeech.id("ancient_shrine"),
        new ShrineFeature(NoneFeatureConfiguration.CODEC)
    );

    private DragonSpeechFeatures() {}

    public static void bootstrap() {
    }
}
