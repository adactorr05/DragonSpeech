package com.dragonspeech.word;

import com.dragonspeech.DragonSpeech;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.HashMap;
import java.util.Map;

/**
 * Loads every word definition from data/<namespace>/dragonspeech_words/*.json
 * across all active datapacks (the base mod's own words, plus anything added
 * by other datapacks or addon mods). This makes the vocabulary fully
 * data-driven and moddable without touching this file.
 *
 * Implements IdentifiableResourceReloadListener (not just
 * SimpleJsonResourceReloadListener) because Fabric's
 * ResourceManagerHelper.registerReloadListener requires an ID for reload
 * ordering purposes - a plain reload listener isn't enough on its own.
 */
public class WordRegistry extends SimpleJsonResourceReloadListener implements IdentifiableResourceReloadListener {

    private static final String DIRECTORY = "dragonspeech_words";
    private static final ResourceLocation RELOAD_LISTENER_ID = DragonSpeech.id("word_registry");

    private static Map<ResourceLocation, Word> words = Map.of();

    public WordRegistry() {
        super(DragonSpeech.GSON, DIRECTORY);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> object, ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<ResourceLocation, Word> parsed = new HashMap<>();

        object.forEach((id, json) -> Word.CODEC.parse(JsonOps.INSTANCE, json).resultOrPartial(
                error -> DragonSpeech.LOGGER.error("[DragonSpeech] Failed to parse word '{}': {}", id, error)
            ).ifPresent(word -> parsed.put(id, word))
        );

        words = Map.copyOf(parsed);
        DragonSpeech.LOGGER.info("[DragonSpeech] Loaded {} Ancient Language word(s)", words.size());
        auditNaturalDiscovery();
    }


    private static void auditNaturalDiscovery() {
        java.util.List<ResourceLocation> noNaturalRoute = words.entrySet().stream()
            .filter(entry -> !hasNaturalWorldRoute(entry.getValue().discoveryMethod()))
            .map(Map.Entry::getKey)
            .sorted(java.util.Comparator.comparing(ResourceLocation::toString))
            .toList();

        java.util.List<String> badPrerequisites = new java.util.ArrayList<>();
        for (var entry : words.entrySet()) {
            for (ResourceLocation prerequisite : entry.getValue().prerequisiteWords()) {
                if (!words.containsKey(prerequisite)) {
                    badPrerequisites.add(entry.getKey() + " -> missing " + prerequisite);
                }
            }
        }

        if (noNaturalRoute.isEmpty() && badPrerequisites.isEmpty()) {
            DragonSpeech.LOGGER.info("[DragonSpeech] Natural word discovery audit: {}/{} words have a world discovery route and all prerequisites resolve.",
                words.size(), words.size());
            return;
        }

        if (!noNaturalRoute.isEmpty()) {
            DragonSpeech.LOGGER.warn("[DragonSpeech] {} word(s) are intentionally/non-naturally discoverable only: {}",
                noNaturalRoute.size(), noNaturalRoute);
        }
        if (!badPrerequisites.isEmpty()) {
            DragonSpeech.LOGGER.error("[DragonSpeech] Broken word prerequisite references: {}", badPrerequisites);
        }
    }

    private static boolean hasNaturalWorldRoute(DiscoveryMethod method) {
        return switch (method) {
            case RUIN_TABLET, MENTOR_NPC, ANCIENT_TEXT, ELVEN_TRIAL, DANGER_WORD -> true;
            case ADMIN_GRANTED, GUESSED -> false;
        };
    }

    @Override
    public ResourceLocation getFabricId() {
        return RELOAD_LISTENER_ID;
    }

    public static Map<ResourceLocation, Word> getAllWords() {
        return words;
    }

    public static Word get(ResourceLocation id) {
        return words.get(id);
    }

    /** Call once from your mod's onInitialize(). */
    public static void register() {
        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new WordRegistry());
    }
}
