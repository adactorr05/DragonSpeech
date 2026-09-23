package com.dragonspeech.client.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Client-side cache of the Server tab's last known state, updated
 * whenever a ConfigSyncPayload arrives (see DragonSpeechClient's
 * receiver registration). ConfigScreen reads from this rather than
 * holding its own copy directly, so a sync that arrives while the
 * screen happens to be closed (e.g. another admin's edit) doesn't get
 * lost - the next time the screen opens it's already current.
 */
public final class ServerConfigClientCache {

    private static JsonObject lastSync = null;

    private ServerConfigClientCache() {}

    public static void update(String json) {
        try {
            lastSync = JsonParser.parseString(json).getAsJsonObject();
        } catch (Exception ignored) {
            // Malformed sync - keep whatever we already had rather than clearing it out.
        }
    }

    public static void clear() {
        lastSync = null;
    }

    public static boolean hasData() {
        return lastSync != null;
    }

    public static boolean canEdit() {
        return lastSync != null && lastSync.has("can_edit") && lastSync.get("can_edit").getAsBoolean();
    }

    public static String difficulty() {
        return lastSync != null && lastSync.has("difficulty") ? lastSync.get("difficulty").getAsString() : "NORMAL";
    }

    public static float difficultyMinSurvivableHealth() {
        return getFloat("difficulty_min_survivable_health", 1.0f);
    }

    public static float difficultyRegenMultiplier() {
        return getFloat("difficulty_regen_multiplier", 1.0f);
    }

    public static float difficultyCostMultiplier() {
        return getFloat("difficulty_cost_multiplier", 1.0f);
    }

    public static float difficultyStructureSpacingMultiplier() {
        return getFloat("difficulty_structure_spacing_multiplier", 1.0f);
    }

    public static boolean allowRebondAfterDeath() {
        return getBool("allow_rebond_after_death", true);
    }

    public static float foundHeartStaminaMin() {
        return getFloat("found_heart_stamina_min", 1000f);
    }

    public static float foundHeartStaminaMax() {
        return getFloat("found_heart_stamina_max", 1510f);
    }

    public static float regenMultiplierExtra() {
        return getFloat("regen_multiplier_extra", 1.0f);
    }

    public static float costMultiplierExtra() {
        return getFloat("cost_multiplier_extra", 1.0f);
    }

    public static float backlashSeverityMultiplier() {
        return getFloat("backlash_severity_multiplier", 1.0f);
    }

    public static boolean allowPvpMindDuels() {
        return getBool("allow_pvp_mind_duels", true);
    }

    public static boolean allowControlOfPlayers() {
        return getBool("allow_control_of_players", false);
    }

    public static float wordLootChanceMultiplier() {
        return getFloat("word_loot_chance_multiplier", 1.0f);
    }

    public static float eggHatchSpeedMultiplier() {
        return getFloat("egg_hatch_speed_multiplier", 1.0f);
    }

    public static boolean bonusChestDragonEggEnabled() {
        return getBool("bonus_chest_dragon_egg_enabled", true);
    }

    public static int aiComputeBudget() {
        return lastSync != null && lastSync.has("ai_compute_budget") ? lastSync.get("ai_compute_budget").getAsInt() : 0;
    }

    /** One difficulty tier's full editable ruleset, as reported by the server's last sync. */
    public record DifficultyTuning(float healthFloor, float regenMultiplier, float costMultiplier, float structureSpacing) {}

    /** The given tier's current tuning, per the last sync. Falls back to the shipped defaults if nothing's synced yet. */
    public static DifficultyTuning difficultyTuning(String tierNameLower) {
        if (lastSync == null || !lastSync.has("difficulty_tuning")) {
            return null;
        }
        JsonObject tuning = lastSync.getAsJsonObject("difficulty_tuning");
        if (!tuning.has(tierNameLower)) {
            return null;
        }
        JsonObject t = tuning.getAsJsonObject(tierNameLower);
        return new DifficultyTuning(
                t.has("health_floor") ? t.get("health_floor").getAsFloat() : 1f,
                t.has("regen_multiplier") ? t.get("regen_multiplier").getAsFloat() : 1f,
                t.has("cost_multiplier") ? t.get("cost_multiplier").getAsFloat() : 1f,
                t.has("structure_spacing") ? t.get("structure_spacing").getAsFloat() : 1f);
    }

    /** One entity's current sentience override, as reported by the server's last sync. */
    public record SentienceOverride(String entityId, String tierName, Integer reactionPower) {}

    /** Every entity that currently has a non-default sentience override, per the last sync. Empty (never null) if nothing's synced yet or nothing's overridden. */
    public static java.util.List<SentienceOverride> sentienceOverrides() {
        java.util.List<SentienceOverride> result = new java.util.ArrayList<>();
        if (lastSync == null || !lastSync.has("sentience_overrides")) {
            return result;
        }
        for (com.google.gson.JsonElement el : lastSync.getAsJsonArray("sentience_overrides")) {
            JsonObject obj = el.getAsJsonObject();
            String id = obj.has("id") ? obj.get("id").getAsString() : null;
            if (id == null) {
                continue;
            }
            String tierName = obj.has("tier") ? obj.get("tier").getAsString() : null;
            Integer reactionPower = obj.has("reaction_power") ? obj.get("reaction_power").getAsInt() : null;
            result.add(new SentienceOverride(id, tierName, reactionPower));
        }
        return result;
    }

    private static float getFloat(String key, float fallback) {
        return lastSync != null && lastSync.has(key) ? lastSync.get(key).getAsFloat() : fallback;
    }

    private static boolean getBool(String key, boolean fallback) {
        return lastSync != null && lastSync.has(key) ? lastSync.get(key).getAsBoolean() : fallback;
    }
}
