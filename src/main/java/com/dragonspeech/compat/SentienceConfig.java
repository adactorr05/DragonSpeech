package com.dragonspeech.compat;

import com.dragonspeech.DragonSpeech;
import com.dragonspeech.mind.MindFortitudeService;
import com.dragonspeech.mind.SentienceTier;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The general-purpose "sentience tagging for modded mobs" system the
 * design notes called for (see DICTIONARY/handoff, "Sentience tagging
 * for modded mobs"): a config file (and, per explicit direction, now
 * also the config GUI's Sentience Editor screen) that assigns a
 * SentienceTier - and, separately, a numeric reaction-speed override -
 * to ANY entity id string: vanilla, this mod's own, or a completely
 * different mod's, without this codebase needing to compile against
 * that mod at all.
 *
 * TWO INDEPENDENT overrides per entity, both optional:
 *   - TIER (SentienceTier - mindless/trivial/instinctual/simple/trained/
 *     disciplined/chaotic/dragon): drives fortitude/focus/stamina, i.e.
 *     how HARD a mind is to reach/read/break. Left as the existing enum
 *     rather than made numeric too - it feeds several different stat
 *     pools at once (see MindFortitudeService), not just one axis, so
 *     turning it fully numeric would be a much bigger, riskier surgery
 *     than what was actually asked for.
 *   - REACTION POWER (a plain unbounded int): drives ONLY how fast that
 *     mind reacts/defends/attacks during a duel (MobMindCombatAI's
 *     clickAttemptsPerPulse) - per explicit direction, "it should not be
 *     limited to only the 3/4 tiers it has... the higher the tier, the
 *     faster that mind reacts." An entity with no reaction-power entry
 *     just falls back to its SentienceTier's own small built-in default
 *     (1/2/4 depending on tier) - this override exists purely to let an
 *     operator go beyond that without also having to change how hard the
 *     mind is to break into.
 *
 * This is deliberately NOT how DragonSpeech's own DRAGON_ENTITY / the
 * Dragon Heart vessel get their tier - those are registered directly and
 * unconditionally via MindFortitudeService.TIER_OVERRIDES.put(...) in
 * their own bootstrap methods, the same way this class registers
 * config-driven entries. This file is the mechanism a THIRD PARTY (or a
 * server operator, with no coding at all - now including through the
 * Sentience Editor GUI) uses to opt an unrelated mod's mobs into the
 * mind-duel system.
 *
 * Resolution happens at server start (bootstrap(), called from
 * DragonSpeech.onInitialize() after all mods have finished registering
 * their entity types) AND again live, any time an override is applied
 * through the GUI or the "Reload Sentience Config" action - both paths
 * go through applyAndPersist()/bootstrap() so they can never drift apart.
 */
public final class SentienceConfig {

    /** One entity's current override state - used both when writing to disk and when reporting the current state back to the config GUI. tier/reactionPower are null when that half isn't overridden. */
    public record Entry(String entityId, SentienceTier tier, Integer reactionPower) {}

    private SentienceConfig() {}

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("dragonspeech").resolve("sentience.json");
    }

    /** Call once from onInitialize(), after entity-type registration but before the server can actually load a world. Also safe to call again later (e.g. the "Reload Sentience Config" action, or after the file is hand-edited) - it fully re-reads the file and re-applies every entry, it doesn't just add to what's already there. */
    public static void bootstrap() {
        MindFortitudeService.TIER_OVERRIDES.clear();
        MindFortitudeService.REACTION_OVERRIDES.clear();
        List<Entry> entries = load(path());
        int applied = 0;
        for (Entry entry : entries) {
            ResourceLocation id = ResourceLocation.tryParse(entry.entityId());
            if (id == null) {
                DragonSpeech.LOGGER.warn("[DragonSpeech] sentience.json: '{}' is not a valid entity id, skipping.", entry.entityId());
                continue;
            }
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
            if (type == null) {
                // Not an error - the mod that owns this entity likely just isn't installed on this
                // server. Exactly the "shouldn't rely on having ice and fire" behavior: the entry is
                // silently inert rather than crashing or logging a scary warning every startup.
                continue;
            }
            if (entry.tier() != null) {
                MindFortitudeService.TIER_OVERRIDES.put(type, entry.tier());
            }
            if (entry.reactionPower() != null) {
                MindFortitudeService.REACTION_OVERRIDES.put(type, entry.reactionPower());
            }
            applied++;
        }
        DragonSpeech.LOGGER.info("[DragonSpeech] Sentience config: {} modded/vanilla entity override(s) applied.", applied);
    }

    /**
     * The Java-side counterpart of the config file - an addon mod (or this mod's own dragon/eldunari
     * bootstrap) can call this directly at startup instead of going through JSON. Thin wrapper so
     * callers never need to know TIER_OVERRIDES lives on MindFortitudeService.
     */
    public static void registerOverride(EntityType<?> type, SentienceTier tier) {
        MindFortitudeService.TIER_OVERRIDES.put(type, tier);
    }

    /**
     * Applies one entity's override live (both in-memory and to disk) - used by the Sentience Editor
     * GUI, both the offline (Title Screen, direct local call) and online (op-4-gated network handler)
     * paths. Passing null for a field clears just that half of the override, leaving the other alone;
     * passing null for BOTH removes the entity's entry from the file entirely.
     */
    public static void applyAndPersist(String entityId, SentienceTier tier, Integer reactionPower) {
        ResourceLocation id = ResourceLocation.tryParse(entityId);
        if (id == null) {
            return;
        }
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
        if (type == null) {
            return;
        }
        if (tier != null) {
            MindFortitudeService.TIER_OVERRIDES.put(type, tier);
        } else {
            MindFortitudeService.TIER_OVERRIDES.remove(type);
        }
        if (reactionPower != null) {
            MindFortitudeService.REACTION_OVERRIDES.put(type, reactionPower);
        } else {
            MindFortitudeService.REACTION_OVERRIDES.remove(type);
        }

        // Persist: re-read the file's raw entries, replace/insert/remove this one id, write it back -
        // every OTHER entity's override in the file is left completely untouched.
        List<Entry> entries = new ArrayList<>(load(path()));
        entries.removeIf(e -> e.entityId().equals(entityId));
        if (tier != null || reactionPower != null) {
            entries.add(new Entry(entityId, tier, reactionPower));
        }
        try {
            save(path(), entries);
        } catch (Exception e) {
            DragonSpeech.LOGGER.warn("[DragonSpeech] Could not save sentience.json: {}", e.getMessage());
        }
    }

    /** "Universal Reset" (config GUI) - clears every sentience override, both in-memory and on disk, and re-bootstraps (which is now a no-op re-read of the now-empty file, just to be certain nothing stale lingers). */
    public static void clearAllOverrides() {
        MindFortitudeService.TIER_OVERRIDES.clear();
        MindFortitudeService.REACTION_OVERRIDES.clear();
        try {
            save(path(), List.of());
        } catch (Exception e) {
            DragonSpeech.LOGGER.warn("[DragonSpeech] Could not clear sentience.json: {}", e.getMessage());
        }
    }

    /** Every currently-applied override, straight from the live in-memory maps (not re-read from disk) - what the config GUI's Sentience Editor shows as each entity's current state. */
    public static List<Entry> currentOverrides() {
        Map<String, SentienceTier> tiers = new LinkedHashMap<>();
        for (var e : MindFortitudeService.TIER_OVERRIDES.entrySet()) {
            ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(e.getKey());
            if (id != null) {
                tiers.put(id.toString(), e.getValue());
            }
        }
        Map<String, Integer> reactions = new LinkedHashMap<>();
        for (var e : MindFortitudeService.REACTION_OVERRIDES.entrySet()) {
            ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(e.getKey());
            if (id != null) {
                reactions.put(id.toString(), e.getValue());
            }
        }
        List<Entry> result = new ArrayList<>();
        java.util.Set<String> allIds = new java.util.LinkedHashSet<>();
        allIds.addAll(tiers.keySet());
        allIds.addAll(reactions.keySet());
        for (String id : allIds) {
            result.add(new Entry(id, tiers.get(id), reactions.get(id)));
        }
        return result;
    }

    private static List<Entry> load(Path path) {
        List<Entry> result = new ArrayList<>();
        try {
            if (Files.exists(path)) {
                JsonObject root = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                if (root.has("overrides")) {
                    JsonObject overrides = root.getAsJsonObject("overrides");
                    for (String key : overrides.keySet()) {
                        JsonElement value = overrides.get(key);
                        if (value.isJsonPrimitive()) {
                            // Old/simple format - a plain tier name string, no reaction-power override.
                            result.add(new Entry(key, parseTier(value.getAsString()), null));
                        } else if (value.isJsonObject()) {
                            // Richer format - {"tier": "...", "reaction_power": N} - either field optional.
                            JsonObject obj = value.getAsJsonObject();
                            SentienceTier tier = obj.has("tier") ? parseTier(obj.get("tier").getAsString()) : null;
                            Integer reaction = obj.has("reaction_power") ? obj.get("reaction_power").getAsInt() : null;
                            result.add(new Entry(key, tier, reaction));
                        }
                    }
                }
            } else {
                writeDefault(path);
            }
        } catch (Exception e) {
            DragonSpeech.LOGGER.error("[DragonSpeech] Failed to read sentience.json, using no overrides this session.", e);
        }
        return result;
    }

    private static void save(Path path, List<Entry> entries) throws Exception {
        Files.createDirectories(path.getParent());
        JsonObject root = new JsonObject();
        root.addProperty("_comment", "Assign a mind-duel SentienceTier and/or a numeric reaction-speed override to ANY entity id - "
            + "vanilla, this mod's own, or another mod's. Valid tiers: mindless, trivial, instinctual, simple, trained, disciplined, "
            + "chaotic, dragon. reaction_power is an unbounded whole number - higher reacts/defends/attacks faster during a duel; "
            + "omit it to just use the tier's own small built-in default. Entries for mods you don't have installed are simply "
            + "ignored, never an error. Editable by hand here, or through the in-game Sentience Editor (config GUI's Server tab).");
        JsonObject overrides = new JsonObject();
        for (Entry entry : entries) {
            if (entry.tier() != null && entry.reactionPower() == null) {
                overrides.addProperty(entry.entityId(), entry.tier().getSerializedName());
            } else {
                JsonObject obj = new JsonObject();
                if (entry.tier() != null) {
                    obj.addProperty("tier", entry.tier().getSerializedName());
                }
                if (entry.reactionPower() != null) {
                    obj.addProperty("reaction_power", entry.reactionPower());
                }
                overrides.add(entry.entityId(), obj);
            }
        }
        root.add("overrides", overrides);
        Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(root));
    }

    private static void writeDefault(Path path) throws Exception {
        save(path, List.of());
    }

    private static SentienceTier parseTier(String raw) {
        for (SentienceTier tier : SentienceTier.values()) {
            if (tier.getSerializedName().equalsIgnoreCase(raw)) {
                return tier;
            }
        }
        return null;
    }
}
