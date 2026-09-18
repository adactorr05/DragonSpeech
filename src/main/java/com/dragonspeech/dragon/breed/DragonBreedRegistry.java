package com.dragonspeech.dragon.breed;

import com.dragonspeech.DragonSpeech;
import com.mojang.serialization.JsonOps;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import com.google.gson.JsonElement;

import java.util.HashMap;
import java.util.Map;

/**
 * Loads every dragon breed from data/<namespace>/dragon_breeds/*.json -
 * exact same structure/pattern as WordRegistry (this package's sibling
 * concern, the Ancient Language vocabulary) - the base mod's 6 breeds
 * (forest, ice, end, fire, void, lightning, gold) plus anything an
 * addon mod or datapack adds under its own namespace, with zero Java
 * changes needed to add a new breed.
 *
 * Concept adapted from Dragon Mounts Legacy (GPL-3.0) - see
 * DragonBreed's own doc for why the loading MECHANISM here is this
 * project's own established SimpleJsonResourceReloadListener pattern
 * rather than a port of DML's RegistrySetBuilder-based system.
 */
public class DragonBreedRegistry extends SimpleJsonResourceReloadListener implements IdentifiableResourceReloadListener {

    private static final String DIRECTORY = "dragon_breeds";
    private static final ResourceLocation RELOAD_LISTENER_ID = DragonSpeech.id("dragon_breed_registry");

    private static Map<ResourceLocation, DragonBreed> breeds = Map.of();

    public DragonBreedRegistry() {
        super(DragonSpeech.GSON, DIRECTORY);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> object, ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<ResourceLocation, DragonBreed> parsed = new HashMap<>();

        object.forEach((id, json) -> DragonBreed.CODEC.parse(JsonOps.INSTANCE, json).resultOrPartial(
                error -> DragonSpeech.LOGGER.error("[DragonSpeech] Failed to parse dragon breed '{}': {}", id, error)
            ).ifPresent(breed -> parsed.put(id, breed))
        );

        breeds = Map.copyOf(parsed);
        DragonSpeech.LOGGER.info("[DragonSpeech] Loaded {} dragon breed(s)", breeds.size());
    }

    @Override
    public ResourceLocation getFabricId() {
        return RELOAD_LISTENER_ID;
    }

    public static Map<ResourceLocation, DragonBreed> getAllBreeds() {
        return breeds;
    }

    public static DragonBreed get(ResourceLocation id) {
        return breeds.get(id);
    }

    /** Fallback for a dragon entity whose saved breed id no longer resolves (e.g. an addon mod that added a breed was removed) - matches Dragon Mounts Legacy's own "fall back to a valid, always-present breed rather than crash" reasoning, per its DragonBreed.getTranslation() doing the same for a missing breed's display name. */
    public static DragonBreed getOrFallback(ResourceLocation id) {
        DragonBreed found = breeds.get(id);
        if (found != null) {
            return found;
        }
        DragonBreed any = breeds.values().stream().findFirst().orElse(null);
        if (any == null) {
            DragonSpeech.LOGGER.error("[DragonSpeech] No dragon breeds loaded at all - '{}' cannot be resolved to anything.", id);
        }
        return any;
    }

    /** Call once from your mod's onInitialize(), same timing/place as WordRegistry.register(). */
    public static void register() {
        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new DragonBreedRegistry());
    }
}
