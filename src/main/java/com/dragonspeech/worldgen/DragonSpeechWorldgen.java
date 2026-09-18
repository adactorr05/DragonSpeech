package com.dragonspeech.worldgen;

import com.dragonspeech.DragonSpeech;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/**
 * Adds the shrine to every overworld biome via Fabric API's
 * BiomeModifications - a code API rather than a raw datapack
 * biome_modifier JSON, matching the mod's existing pattern of preferring
 * code-registered hooks (LootTableEvents.MODIFY, etc.) over hand-written
 * datapack files wherever Fabric API offers the choice.
 *
 * FIX: "all of these can spawn on the surface of the ocean. I dont want
 * this to happen. Same with the shrine structure" per explicit
 * direction - BiomeSelectors.foundInOverworld() includes every overworld
 * biome, oceans and rivers included. Now uses BiomeSelectors.tag(...)
 * against the same dragonspeech:generates_on_land tag the 4 jigsaw
 * structures were just switched to (see their structure.json files) -
 * one shared list of land biomes instead of two separate, potentially
 * drifting definitions of "land" between the feature and the
 * structures.
 *
 * VERSION-RISK NOTE: BiomeModifications' exact registration call
 * (ADD_FEATURE vs add(...) overloads) has shifted across fabric-api
 * versions more than most APIs in this project. If this doesn't compile,
 * search your fabric-api sources for "BiomeModifications" - the shape
 * (select biomes, specify a GenerationStep, add a PlacedFeature key)
 * has stayed conceptually the same even as method names moved.
 */
public final class DragonSpeechWorldgen {

    private static final ResourceKey<PlacedFeature> ANCIENT_SHRINE_PLACED = ResourceKey.create(
        Registries.PLACED_FEATURE, DragonSpeech.id("ancient_shrine_placed"));

    private static final TagKey<Biome> GENERATES_ON_LAND = TagKey.create(
        Registries.BIOME, DragonSpeech.id("generates_on_land"));

    private DragonSpeechWorldgen() {}

    public static void register() {
        BiomeModifications.addFeature(
            BiomeSelectors.tag(GENERATES_ON_LAND),
            GenerationStep.Decoration.SURFACE_STRUCTURES,
            ANCIENT_SHRINE_PLACED
        );
    }
}
