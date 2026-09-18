package com.dragonspeech.config;

import com.dragonspeech.DragonSpeech;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Server-side config, loaded from config/dragonspeech.json (written with
 * defaults on first launch) - "Magic Difficulty," a setting deliberately
 * independent of vanilla Game Difficulty (you can run Normal survival
 * with Hard magic, or the reverse). World-wide, not per-player: whatever
 * this file says applies to every player on the world/server equally.
 *
 *   EASY   - overdraft can never take you below 2 hearts; stamina
 *            regenerates faster AND spells cost less to begin with
 *   NORMAL - overdraft can never take you below half a heart (the
 *            original, still the default)
 *   HARD   - no floor at all - drawing more than you have WILL kill you,
 *            the same as any other source of lethal damage
 *
 * This only ever governs CASTING (and anything that spends a caster's
 * stamina/hunger/health the same way casting does, like an
 * aflbinda-linked barrier or ward). Learning words never costs anything
 * on any difficulty - knowledge is free; wielding it is what has a
 * price.
 *
 * Settable two ways, deliberately: directly in this config file (works
 * for a dedicated server admin who never sees a world-creation screen),
 * or via a dedicated tab on the Create New World screen for singleplayer
 * convenience (see MagicDifficultyTab) - both just end up writing the
 * same "difficulty" field in the same file.
 */
public final class DragonSpeechConfig {

    public enum Difficulty { EASY, NORMAL, HARD }

    private static Difficulty difficulty = Difficulty.NORMAL;

    /**
     * "I want a config that opens a sub config that edits what each magic difficulty does" per
     * explicit direction. These 4 maps (one entry per Difficulty) are what minSurvivableHealth()/
     * regenMultiplier()/costMultiplier()/structureSpacingMultiplier() now read from, instead of the
     * fixed per-difficulty numbers those methods used to hardcode directly. Editable from the config
     * GUI's DifficultyTuningScreen (opened from the Server tab's "Edit Magic Difficulty Rules"
     * button) - same op-level-4/offline-vs-online rules as everything else on that tab. Which
     * difficulty is CURRENTLY ACTIVE is still a completely separate thing (the `difficulty` field
     * above), still only ever set via the Create World screen or hand-editing the file - this only
     * changes what each of the 3 named tiers actually MEANS, never which one is picked.
     *
     * DEFAULT_* below are the original hardcoded values, preserved as named constants specifically so
     * the "Reset to Defaults" button in that screen has something authoritative to reset back to.
     */
    private static final float DEFAULT_HEALTH_FLOOR_EASY = 4.0f;
    private static final float DEFAULT_HEALTH_FLOOR_NORMAL = 1.0f;
    private static final float DEFAULT_HEALTH_FLOOR_HARD = 0.0f;
    private static final float DEFAULT_REGEN_EASY = 1.5f;
    private static final float DEFAULT_REGEN_NORMAL = 1.0f;
    private static final float DEFAULT_REGEN_HARD = 1.0f;
    private static final float DEFAULT_COST_EASY = 0.75f;
    private static final float DEFAULT_COST_NORMAL = 1.0f;
    private static final float DEFAULT_COST_HARD = 1.0f;
    private static final float DEFAULT_SPACING_EASY = 1.0f;
    private static final float DEFAULT_SPACING_NORMAL = 1.4f;
    private static final float DEFAULT_SPACING_HARD = 1.8f;

    private static final Map<Difficulty, Float> healthFloorByDifficulty = defaultHealthFloors();
    private static final Map<Difficulty, Float> regenMultiplierByDifficulty = defaultRegenMultipliers();
    private static final Map<Difficulty, Float> costMultiplierByDifficulty = defaultCostMultipliers();
    private static final Map<Difficulty, Float> spacingMultiplierByDifficulty = defaultSpacingMultipliers();

    private static Map<Difficulty, Float> defaultHealthFloors() {
        Map<Difficulty, Float> m = new java.util.EnumMap<>(Difficulty.class);
        m.put(Difficulty.EASY, DEFAULT_HEALTH_FLOOR_EASY);
        m.put(Difficulty.NORMAL, DEFAULT_HEALTH_FLOOR_NORMAL);
        m.put(Difficulty.HARD, DEFAULT_HEALTH_FLOOR_HARD);
        return m;
    }

    private static Map<Difficulty, Float> defaultRegenMultipliers() {
        Map<Difficulty, Float> m = new java.util.EnumMap<>(Difficulty.class);
        m.put(Difficulty.EASY, DEFAULT_REGEN_EASY);
        m.put(Difficulty.NORMAL, DEFAULT_REGEN_NORMAL);
        m.put(Difficulty.HARD, DEFAULT_REGEN_HARD);
        return m;
    }

    private static Map<Difficulty, Float> defaultCostMultipliers() {
        Map<Difficulty, Float> m = new java.util.EnumMap<>(Difficulty.class);
        m.put(Difficulty.EASY, DEFAULT_COST_EASY);
        m.put(Difficulty.NORMAL, DEFAULT_COST_NORMAL);
        m.put(Difficulty.HARD, DEFAULT_COST_HARD);
        return m;
    }

    private static Map<Difficulty, Float> defaultSpacingMultipliers() {
        Map<Difficulty, Float> m = new java.util.EnumMap<>(Difficulty.class);
        m.put(Difficulty.EASY, DEFAULT_SPACING_EASY);
        m.put(Difficulty.NORMAL, DEFAULT_SPACING_NORMAL);
        m.put(Difficulty.HARD, DEFAULT_SPACING_HARD);
        return m;
    }

    /** "There should only be 1 bonded dragon each player alive at a time. The config option gives it another piece where you can stop players from bonding to another if their previous bonded dragon dies" per explicit direction. TRUE = a player may bond a new dragon once their previous one has died. FALSE = once their one bonded dragon dies, that player can never bond again. Default TRUE per explicit direction. */
    private static boolean allowRebondAfterDeath = true;

    /**
     * "Dragon hearts that are found within the world or just gotten in
     * creative... their stamina should be randomized from an Adult
     * dragon's stamina to an Elder or Ancient stamina" per explicit
     * direction. Only applies to hearts with no living source dragon
     * (found/looted/creative) - a heart taken from an actual bonded
     * dragon via the Give Heart button already correctly uses that real
     * dragon's own age-locked stamina (see HEART_MAX_ENERGY's own doc),
     * completely untouched by this.
     *
     * Defaults computed directly from DragonAgeStage's own real
     * healthMultiplier formula (150 + 850*multiplier) - 1000 for Adult,
     * 1510 for Ancient - not arbitrary numbers.
     */
    private static float foundHeartStaminaMin = 1000f;
    private static float foundHeartStaminaMax = 1510f;

    // ---- Added for the config GUI (Server tab) - see ConfigScreen ----
    // Every field below is a SEPARATE knob layered on TOP of whatever
    // Magic Difficulty already contributes (regenMultiplier()/
    // costMultiplier() above) - which difficulty is CURRENTLY ACTIVE is
    // still ONLY settable from the Create World screen / this file
    // directly, per explicit direction ("I don't want it moved"). These
    // are additive server-wide multipliers/toggles for admins who want
    // more control than the 3-tier difficulty alone gives them, without
    // touching the difficulty system itself.

    /** Extra multiplier on top of regenMultiplier() (difficulty's own). 1.0 = no change. Applied in StaminaTicker. */
    private static float regenMultiplierExtra = 1.0f;
    /** Extra multiplier on top of costMultiplier() (difficulty's own). 1.0 = no change. Applied in CastExecutor. */
    private static float costMultiplierExtra = 1.0f;
    /** Scales BacklashResolver's base stamina/health cost for a wrong guess. 1.0 = unchanged, 0.0 = no backlash cost at all. */
    private static float backlashSeverityMultiplier = 1.0f;
    /** Whether a player may Reach Out / start a Mind Duel against another real player at all. Mob duels are never affected by this. Default true (unchanged prior behavior). */
    private static boolean allowPvpMindDuels = true;
    /** Whether the "Control" command effect (won-duel puppeteering, see MindControlService) may be used against another real player's body. Does NOT affect Possession (hambinda), which is unconditionally mob-only regardless of this setting. Default false - this is a much bigger deal than simply dueling, so it defaults OFF even though PvP duels themselves default ON. */
    private static boolean allowControlOfPlayers = false;
    /** Multiplies every word-teaching loot chance (tablets + scholar's fragments) injected by LootInjection. 1.0 = unchanged. Takes effect on the next loot-table reload (world (re)load, or /reload), same as any other datapack-driven loot change. */
    private static float wordLootChanceMultiplier = 1.0f;
    /** Multiplies a dragon egg's per-tick hatch-roll chance (VariantDragonEggBlock#randomTick). Higher = hatches faster on average. 1.0 = unchanged. */
    private static float eggHatchSpeedMultiplier = 1.0f;

    /**
     * "A server owner might want to cap total AI compute regardless of which addon is running" - a
     * design-doc-flagged open question for the Neural Network addon, resolved here as a base-mod
     * setting rather than something every addon has to reimplement independently. Caps how many
     * actor-decisions MobMindCombatAI's actTick() processes per pulse, TOTAL, across every active
     * duel - enforced centrally in MobMindCombatAI itself, so it applies equally to the built-in
     * heuristic AND any registered addon brain without either needing to know the setting exists.
     * 0 = unlimited (the original, unthrottled behavior) - this is opt-in for server owners who
     * actually need it, not a default performance tax on everyone.
     */
    private static int aiComputeBudget = 0;

    private DragonSpeechConfig() {}

    public static Difficulty difficulty() {
        return difficulty;
    }

    public static void setDifficulty(Difficulty newDifficulty) {
        difficulty = newDifficulty;
        save();
    }

    /** The health floor overdraft cannot push past, for the CURRENTLY ACTIVE difficulty. Editable per-tier now (see DifficultyTuningScreen) - 0 means death is genuinely on the table, same as vanilla starvation only being lethal on Hard game difficulty. */
    public static float minSurvivableHealth() {
        return healthFloorByDifficulty.get(difficulty);
    }

    public static float minSurvivableHealth(Difficulty tier) {
        return healthFloorByDifficulty.get(tier);
    }

    public static void setMinSurvivableHealth(Difficulty tier, float value) {
        healthFloorByDifficulty.put(tier, clamp(value, 0f, 40f));
        save();
    }

    /** Multiplies StaminaTicker's regen-per-second, for the CURRENTLY ACTIVE difficulty. Editable per-tier now (see DifficultyTuningScreen). */
    public static float regenMultiplier() {
        return regenMultiplierByDifficulty.get(difficulty);
    }

    public static float regenMultiplier(Difficulty tier) {
        return regenMultiplierByDifficulty.get(tier);
    }

    public static void setRegenMultiplier(Difficulty tier, float value) {
        regenMultiplierByDifficulty.put(tier, clamp(value, 0.1f, 5.0f));
        save();
    }

    /** Multiplies a spell's final stamina cost, applied once in CastExecutor right before payment, for the CURRENTLY ACTIVE difficulty. Editable per-tier now (see DifficultyTuningScreen). */
    public static float costMultiplier() {
        return costMultiplierByDifficulty.get(difficulty);
    }

    public static float costMultiplier(Difficulty tier) {
        return costMultiplierByDifficulty.get(tier);
    }

    public static void setCostMultiplier(Difficulty tier, float value) {
        costMultiplierByDifficulty.put(tier, clamp(value, 0.1f, 5.0f));
        save();
    }

    /**
     * Multiplies each dragonspeech structure_set's spacing (see RandomSpreadStructurePlacementMixin,
     * the only thing that reads this) for the CURRENTLY ACTIVE difficulty - smaller spacing means MORE
     * frequent. Editable per-tier now (see DifficultyTuningScreen); originally EASY was anchored at
     * 1.0x (matching each structure_set.json's own village-comparable base values) with NORMAL/HARD
     * scaling progressively rarer from there - that's still the shipped default, just no longer fixed.
     */
    public static float structureSpacingMultiplier() {
        return spacingMultiplierByDifficulty.get(difficulty);
    }

    public static float structureSpacingMultiplier(Difficulty tier) {
        return spacingMultiplierByDifficulty.get(tier);
    }

    public static void setStructureSpacingMultiplier(Difficulty tier, float value) {
        spacingMultiplierByDifficulty.put(tier, clamp(value, 0.1f, 10.0f));
        save();
    }

    /** Restores all 12 difficulty-tuning numbers (health floor/regen/cost/structure spacing x 3 tiers) to their original shipped defaults - the config GUI's "Reset to Defaults" button. */
    public static void resetDifficultyTuningToDefaults() {
        healthFloorByDifficulty.putAll(defaultHealthFloors());
        regenMultiplierByDifficulty.putAll(defaultRegenMultipliers());
        costMultiplierByDifficulty.putAll(defaultCostMultipliers());
        spacingMultiplierByDifficulty.putAll(defaultSpacingMultipliers());
        save();
    }

    /**
     * "Reset This Page" (Server tab) in the config GUI - restores the 10 fields ADDED for the GUI
     * (rebond/heart-stamina/extra-multipliers/pvp-toggles/loot/hatch-speed) to their shipped
     * defaults. Deliberately does NOT touch Magic Difficulty's own active selection, the per-tier
     * tuning numbers (see resetDifficultyTuningToDefaults(), its own separate reset button on
     * DifficultyTuningScreen), or sentience overrides (see SentienceConfig.clearAllOverrides(), its
     * own separate button on the Sentience Editor) - each of those 3 areas resets independently so a
     * "reset this page" click can't silently wipe out edits made somewhere else entirely.
     */
    public static void resetServerExtrasToDefaults() {
        allowRebondAfterDeath = true;
        foundHeartStaminaMin = 1000f;
        foundHeartStaminaMax = 1510f;
        regenMultiplierExtra = 1.0f;
        costMultiplierExtra = 1.0f;
        backlashSeverityMultiplier = 1.0f;
        allowPvpMindDuels = true;
        allowControlOfPlayers = false;
        wordLootChanceMultiplier = 1.0f;
        eggHatchSpeedMultiplier = 1.0f;
        aiComputeBudget = 0;
        save();
    }

    public static boolean allowRebondAfterDeath() {
        return allowRebondAfterDeath;
    }

    /** Added for the config GUI - this field previously had no way to be changed except editing the file by hand (or the pre-GUI absence of any setter at all). */
    public static void setAllowRebondAfterDeath(boolean value) {
        allowRebondAfterDeath = value;
        save();
    }

    public static float foundHeartStaminaMin() {
        return foundHeartStaminaMin;
    }

    /** Added for the config GUI. Clamped to be non-negative and never above the current max (swapping them silently would be confusing in a slider UI). */
    public static void setFoundHeartStaminaMin(float value) {
        foundHeartStaminaMin = Math.max(0f, Math.min(value, foundHeartStaminaMax));
        save();
    }

    public static float foundHeartStaminaMax() {
        return foundHeartStaminaMax;
    }

    /** Added for the config GUI. Clamped to never fall below the current min. */
    public static void setFoundHeartStaminaMax(float value) {
        foundHeartStaminaMax = Math.max(value, foundHeartStaminaMin);
        save();
    }

    public static float regenMultiplierExtra() {
        return regenMultiplierExtra;
    }

    public static void setRegenMultiplierExtra(float value) {
        regenMultiplierExtra = clamp(value, 0.1f, 5.0f);
        save();
    }

    public static float costMultiplierExtra() {
        return costMultiplierExtra;
    }

    public static void setCostMultiplierExtra(float value) {
        costMultiplierExtra = clamp(value, 0.1f, 5.0f);
        save();
    }

    public static float backlashSeverityMultiplier() {
        return backlashSeverityMultiplier;
    }

    public static void setBacklashSeverityMultiplier(float value) {
        backlashSeverityMultiplier = clamp(value, 0.0f, 5.0f);
        save();
    }

    public static boolean allowPvpMindDuels() {
        return allowPvpMindDuels;
    }

    public static void setAllowPvpMindDuels(boolean value) {
        allowPvpMindDuels = value;
        save();
    }

    public static boolean allowControlOfPlayers() {
        return allowControlOfPlayers;
    }

    public static void setAllowControlOfPlayers(boolean value) {
        allowControlOfPlayers = value;
        save();
    }

    public static float wordLootChanceMultiplier() {
        return wordLootChanceMultiplier;
    }

    public static void setWordLootChanceMultiplier(float value) {
        wordLootChanceMultiplier = clamp(value, 0.0f, 3.0f);
        save();
    }

    public static float eggHatchSpeedMultiplier() {
        return eggHatchSpeedMultiplier;
    }

    public static void setEggHatchSpeedMultiplier(float value) {
        eggHatchSpeedMultiplier = clamp(value, 0.1f, 5.0f);
        save();
    }

    public static int aiComputeBudget() {
        return aiComputeBudget;
    }

    public static void setAiComputeBudget(int value) {
        aiComputeBudget = Math.max(0, value);
        save();
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    public static void load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("dragonspeech.json");
        try {
            if (Files.exists(path)) {
                JsonObject root = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                if (root.has("difficulty")) {
                    difficulty = parseDifficulty(root.get("difficulty").getAsString());
                }
                if (root.has("difficulty_tuning")) {
                    JsonObject tuning = root.getAsJsonObject("difficulty_tuning");
                    for (Difficulty tier : Difficulty.values()) {
                        String key = tier.name().toLowerCase();
                        if (!tuning.has(key)) {
                            continue;
                        }
                        JsonObject tierObj = tuning.getAsJsonObject(key);
                        if (tierObj.has("health_floor")) {
                            healthFloorByDifficulty.put(tier, tierObj.get("health_floor").getAsFloat());
                        }
                        if (tierObj.has("regen_multiplier")) {
                            regenMultiplierByDifficulty.put(tier, tierObj.get("regen_multiplier").getAsFloat());
                        }
                        if (tierObj.has("cost_multiplier")) {
                            costMultiplierByDifficulty.put(tier, tierObj.get("cost_multiplier").getAsFloat());
                        }
                        if (tierObj.has("structure_spacing")) {
                            spacingMultiplierByDifficulty.put(tier, tierObj.get("structure_spacing").getAsFloat());
                        }
                    }
                }
                if (root.has("allow_rebond_after_death")) {
                    allowRebondAfterDeath = root.get("allow_rebond_after_death").getAsBoolean();
                }
                if (root.has("found_heart_stamina_min")) {
                    foundHeartStaminaMin = root.get("found_heart_stamina_min").getAsFloat();
                }
                if (root.has("found_heart_stamina_max")) {
                    foundHeartStaminaMax = root.get("found_heart_stamina_max").getAsFloat();
                }
                if (root.has("regen_multiplier_extra")) {
                    regenMultiplierExtra = root.get("regen_multiplier_extra").getAsFloat();
                }
                if (root.has("cost_multiplier_extra")) {
                    costMultiplierExtra = root.get("cost_multiplier_extra").getAsFloat();
                }
                if (root.has("backlash_severity_multiplier")) {
                    backlashSeverityMultiplier = root.get("backlash_severity_multiplier").getAsFloat();
                }
                if (root.has("allow_pvp_mind_duels")) {
                    allowPvpMindDuels = root.get("allow_pvp_mind_duels").getAsBoolean();
                }
                if (root.has("allow_control_of_players")) {
                    allowControlOfPlayers = root.get("allow_control_of_players").getAsBoolean();
                }
                if (root.has("word_loot_chance_multiplier")) {
                    wordLootChanceMultiplier = root.get("word_loot_chance_multiplier").getAsFloat();
                }
                if (root.has("egg_hatch_speed_multiplier")) {
                    eggHatchSpeedMultiplier = root.get("egg_hatch_speed_multiplier").getAsFloat();
                }
                if (root.has("ai_compute_budget")) {
                    aiComputeBudget = root.get("ai_compute_budget").getAsInt();
                }
            } else {
                writeDefaultFile(path);
            }
            DragonSpeech.LOGGER.info("[DragonSpeech] Magic difficulty: {}", difficulty);
        } catch (Exception e) {
            DragonSpeech.LOGGER.warn("[DragonSpeech] Could not read config, using defaults: {}", e.getMessage());
        }
    }

    /** Writes the CURRENT in-memory difficulty back to disk - called by setDifficulty() (e.g. from the world-creation tab) so a change made in-game actually persists the same way a manual file edit would. */
    public static void save() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("dragonspeech.json");
        try {
            writeDefaultFile(path);
        } catch (Exception e) {
            DragonSpeech.LOGGER.warn("[DragonSpeech] Could not save config: {}", e.getMessage());
        }
    }

    private static void writeDefaultFile(Path path) throws Exception {
        JsonObject root = new JsonObject();
        root.addProperty("difficulty", difficulty.name());
        root.addProperty("_difficulty_options", "EASY | NORMAL | HARD - how far magic overdraft can drain your health, and how forgiving stamina cost/regen is. World-wide setting, same for every player.");

        JsonObject tuning = new JsonObject();
        for (Difficulty tier : Difficulty.values()) {
            JsonObject tierObj = new JsonObject();
            tierObj.addProperty("health_floor", healthFloorByDifficulty.get(tier));
            tierObj.addProperty("regen_multiplier", regenMultiplierByDifficulty.get(tier));
            tierObj.addProperty("cost_multiplier", costMultiplierByDifficulty.get(tier));
            tierObj.addProperty("structure_spacing", spacingMultiplierByDifficulty.get(tier));
            tuning.add(tier.name().toLowerCase(), tierObj);
        }
        root.add("difficulty_tuning", tuning);
        root.addProperty("_difficulty_tuning_options", "What each of the 3 difficulty tiers above actually DOES - health_floor is in raw health points (2 = 1 heart), the rest are plain multipliers. Editable by hand here, or through the in-game DifficultyTuningScreen (config GUI's Server tab, \"Edit Magic Difficulty Rules\").");
        root.addProperty("allow_rebond_after_death", allowRebondAfterDeath);
        root.addProperty("_allow_rebond_after_death_options", "true | false - whether a player may bond a new dragon after their previous bonded dragon has died. A player can only ever have 1 bonded dragon alive at a time regardless of this setting - this only controls what happens AFTER that one dies.");
        root.addProperty("found_heart_stamina_min", foundHeartStaminaMin);
        root.addProperty("found_heart_stamina_max", foundHeartStaminaMax);
        root.addProperty("_found_heart_stamina_options", "The random stamina range for a Dragon Heart with no living source dragon (found in the world, or obtained in creative) - rolled once, per heart, the first time it's actually broken/used. Defaults are the real Adult (1000) and Ancient (1510) stamina values, computed from DragonAgeStage's own age-scaling formula. Does NOT affect a heart taken from an actual bonded dragon via the Give Heart button - that always uses that specific dragon's own real current stamina, exactly as its age actually is.");
        root.addProperty("regen_multiplier_extra", regenMultiplierExtra);
        root.addProperty("cost_multiplier_extra", costMultiplierExtra);
        root.addProperty("_extra_multiplier_options", "Independent multipliers layered ON TOP of whatever Magic Difficulty already applies - not a replacement for it. 1.0 = no extra change either way.");
        root.addProperty("backlash_severity_multiplier", backlashSeverityMultiplier);
        root.addProperty("allow_pvp_mind_duels", allowPvpMindDuels);
        root.addProperty("allow_control_of_players", allowControlOfPlayers);
        root.addProperty("word_loot_chance_multiplier", wordLootChanceMultiplier);
        root.addProperty("egg_hatch_speed_multiplier", eggHatchSpeedMultiplier);
        root.addProperty("ai_compute_budget", aiComputeBudget);
        root.addProperty("_ai_compute_budget_options", "Caps how many mind-duel AI actor-decisions run per pulse, total, server-wide - applies to the built-in heuristic AND any addon-registered brain alike. 0 = unlimited.");
        Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(root));
    }

    /** Backward-compatible with the old 4-tier config (EASY/NORMAL/HARD/HARDCORE) - an existing file with "HARDCORE" written in it maps to the new HARD (lethal) rather than crashing on an unknown enum value. */
    private static Difficulty parseDifficulty(String raw) {
        String upper = raw.toUpperCase();
        if (upper.equals("HARDCORE")) {
            return Difficulty.HARD;
        }
        try {
            return Difficulty.valueOf(upper);
        } catch (IllegalArgumentException e) {
            return Difficulty.NORMAL;
        }
    }
}