package com.dragonspeech.network;

import com.dragonspeech.compat.SentienceConfig;
import com.dragonspeech.config.DragonSpeechConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Backs the config GUI's Server tab: builds the outgoing snapshot,
 * applies an incoming one (re-validating permission first, never
 * trusting the client), and runs the sentience.json reload action.
 *
 * Permission check used everywhere here: ServerPlayer#hasPermissions(4)
 * - full op only, per explicit direction ("Use permission level 4 (full
 * op only)"). In singleplayer the world owner is op level 4 by default,
 * so this naturally covers "Owner/Server/LAN Host/World Host" without
 * needing any singleplayer-specific special-casing - the integrated
 * server already grants the owner that level automatically.
 */
public final class ConfigRequestHandler {

    private ConfigRequestHandler() {}

    /** Called when a ConfigRequestPayload arrives - builds and sends back the current snapshot. */
    public static void handleRequest(ServerPlayer player) {
        DragonSpeechNetworking.sendConfigSync(player, buildSyncJson(player));
    }

    /** Called when a ConfigUpdatePayload arrives - re-validates permission, then applies each field present. */
    public static void handleUpdate(ServerPlayer player, String json) {
        if (!player.hasPermissions(4)) {
            player.sendSystemMessage(Component.literal("You don't have permission to change server-wide DragonSpeech settings."));
            // Send back the real (unchanged) state so a client that somehow got here with a stale/
            // tampered UI snaps back to what's actually in effect, rather than showing its own rejected edit.
            DragonSpeechNetworking.sendConfigSync(player, buildSyncJson(player));
            return;
        }
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (root.has("allow_rebond_after_death")) {
                // No dedicated setter exists for this one (only set via setDifficulty-style direct field
                // access previously) - route it the same way every other value here is routed, through
                // the config's own public setters, added alongside the rest of the GUI wiring.
                DragonSpeechConfig.setAllowRebondAfterDeath(root.get("allow_rebond_after_death").getAsBoolean());
            }
            if (root.has("found_heart_stamina_min")) {
                DragonSpeechConfig.setFoundHeartStaminaMin(root.get("found_heart_stamina_min").getAsFloat());
            }
            if (root.has("found_heart_stamina_max")) {
                DragonSpeechConfig.setFoundHeartStaminaMax(root.get("found_heart_stamina_max").getAsFloat());
            }
            if (root.has("regen_multiplier_extra")) {
                DragonSpeechConfig.setRegenMultiplierExtra(root.get("regen_multiplier_extra").getAsFloat());
            }
            if (root.has("cost_multiplier_extra")) {
                DragonSpeechConfig.setCostMultiplierExtra(root.get("cost_multiplier_extra").getAsFloat());
            }
            if (root.has("backlash_severity_multiplier")) {
                DragonSpeechConfig.setBacklashSeverityMultiplier(root.get("backlash_severity_multiplier").getAsFloat());
            }
            if (root.has("allow_pvp_mind_duels")) {
                DragonSpeechConfig.setAllowPvpMindDuels(root.get("allow_pvp_mind_duels").getAsBoolean());
            }
            if (root.has("allow_control_of_players")) {
                DragonSpeechConfig.setAllowControlOfPlayers(root.get("allow_control_of_players").getAsBoolean());
            }
            if (root.has("word_loot_chance_multiplier")) {
                DragonSpeechConfig.setWordLootChanceMultiplier(root.get("word_loot_chance_multiplier").getAsFloat());
            }
            if (root.has("egg_hatch_speed_multiplier")) {
                DragonSpeechConfig.setEggHatchSpeedMultiplier(root.get("egg_hatch_speed_multiplier").getAsFloat());
            }
            if (root.has("ai_compute_budget")) {
                DragonSpeechConfig.setAiComputeBudget(root.get("ai_compute_budget").getAsInt());
            }
        } catch (Exception e) {
            // Malformed payload from a stale/tampered client - ignore it rather than crash the server thread.
        }
        // Echo the real post-apply state back (and to every other admin who might have the screen open -
        // see DragonSpeechNetworking.broadcastConfigSyncToAdmins) so every open config screen stays honest.
        DragonSpeechNetworking.broadcastConfigSyncToAdmins(player.getServer());
    }

    /** Called when a ReloadSentiencePayload arrives. */
    public static void handleReloadSentience(ServerPlayer player) {
        if (!player.hasPermissions(4)) {
            player.sendSystemMessage(Component.literal("You don't have permission to reload the sentience config."));
            return;
        }
        SentienceConfig.bootstrap();
        player.sendSystemMessage(Component.literal("sentience.json reloaded."));
        DragonSpeechNetworking.broadcastConfigSyncToAdmins(player.getServer());
    }

    /** Called when a SetSentienceOverridePayload arrives - one entity's tier/reaction-power edit from the Sentience Editor. */
    public static void handleSentienceUpdate(ServerPlayer player, SetSentienceOverridePayload payload) {
        if (!player.hasPermissions(4)) {
            player.sendSystemMessage(Component.literal("You don't have permission to edit sentience overrides."));
            return;
        }
        com.dragonspeech.mind.SentienceTier tier = null;
        if (payload.hasTier()) {
            for (com.dragonspeech.mind.SentienceTier candidate : com.dragonspeech.mind.SentienceTier.values()) {
                if (candidate.getSerializedName().equalsIgnoreCase(payload.tierName())) {
                    tier = candidate;
                    break;
                }
            }
        }
        Integer reactionPower = payload.hasReactionPower() ? payload.reactionPower() : null;
        SentienceConfig.applyAndPersist(payload.entityId(), tier, reactionPower);
    }

    /** Called when a SetDifficultyTuningPayload arrives - one tier's 4-value ruleset edit from DifficultyTuningScreen. */
    public static void handleDifficultyTuningUpdate(ServerPlayer player, SetDifficultyTuningPayload payload) {
        if (!player.hasPermissions(4)) {
            player.sendSystemMessage(Component.literal("You don't have permission to edit magic difficulty rules."));
            return;
        }
        DragonSpeechConfig.Difficulty tier;
        try {
            tier = DragonSpeechConfig.Difficulty.valueOf(payload.tierName().toUpperCase());
        } catch (IllegalArgumentException e) {
            return;
        }
        DragonSpeechConfig.setMinSurvivableHealth(tier, payload.healthFloor());
        DragonSpeechConfig.setRegenMultiplier(tier, payload.regenMultiplier());
        DragonSpeechConfig.setCostMultiplier(tier, payload.costMultiplier());
        DragonSpeechConfig.setStructureSpacingMultiplier(tier, payload.structureSpacing());
        DragonSpeechNetworking.broadcastConfigSyncToAdmins(player.getServer());
    }

    /** Called when a ResetDifficultyTuningPayload arrives - the "Reset to Defaults" button. */
    public static void handleDifficultyTuningReset(ServerPlayer player) {
        if (!player.hasPermissions(4)) {
            player.sendSystemMessage(Component.literal("You don't have permission to reset magic difficulty rules."));
            return;
        }
        DragonSpeechConfig.resetDifficultyTuningToDefaults();
        player.sendSystemMessage(Component.literal("Magic difficulty rules reset to defaults."));
        DragonSpeechNetworking.broadcastConfigSyncToAdmins(player.getServer());
    }

    /** Called when a ResetServerTabPayload arrives - "Reset This Page" on the Server tab. */
    public static void handleResetServerTab(ServerPlayer player) {
        if (!player.hasPermissions(4)) {
            player.sendSystemMessage(Component.literal("You don't have permission to reset server settings."));
            return;
        }
        DragonSpeechConfig.resetServerExtrasToDefaults();
        player.sendSystemMessage(Component.literal("Server tab settings reset to defaults."));
        DragonSpeechNetworking.broadcastConfigSyncToAdmins(player.getServer());
    }

    /** Called when a ResetAllConfigPayload arrives - the universal "Reset All Configs" button. */
    public static void handleResetAllConfig(ServerPlayer player) {
        if (!player.hasPermissions(4)) {
            player.sendSystemMessage(Component.literal("You don't have permission to reset server settings."));
            return;
        }
        DragonSpeechConfig.resetServerExtrasToDefaults();
        DragonSpeechConfig.resetDifficultyTuningToDefaults();
        SentienceConfig.clearAllOverrides();
        player.sendSystemMessage(Component.literal("All DragonSpeech server settings reset to defaults."));
        DragonSpeechNetworking.broadcastConfigSyncToAdmins(player.getServer());
    }

    /** Called when a ClearSentienceOverridesPayload arrives - "Clear All Overrides" on the Sentience Editor. */
    public static void handleClearSentienceOverrides(ServerPlayer player) {
        if (!player.hasPermissions(4)) {
            player.sendSystemMessage(Component.literal("You don't have permission to clear sentience overrides."));
            return;
        }
        SentienceConfig.clearAllOverrides();
        player.sendSystemMessage(Component.literal("All sentience overrides cleared."));
        DragonSpeechNetworking.broadcastConfigSyncToAdmins(player.getServer());
    }

    public static String buildSyncJson(ServerPlayer player) {
        JsonObject root = new JsonObject();
        root.addProperty("can_edit", player.hasPermissions(4));

        // Magic Difficulty breakdown for the CURRENTLY ACTIVE tier - unchanged, still read-only here.
        root.addProperty("difficulty", DragonSpeechConfig.difficulty().name());
        root.addProperty("difficulty_min_survivable_health", DragonSpeechConfig.minSurvivableHealth());
        root.addProperty("difficulty_regen_multiplier", DragonSpeechConfig.regenMultiplier());
        root.addProperty("difficulty_cost_multiplier", DragonSpeechConfig.costMultiplier());
        root.addProperty("difficulty_structure_spacing_multiplier", DragonSpeechConfig.structureSpacingMultiplier());

        // DifficultyTuningScreen - the full editable ruleset for ALL 3 tiers, not just the active one.
        JsonObject tuning = new JsonObject();
        for (DragonSpeechConfig.Difficulty tier : DragonSpeechConfig.Difficulty.values()) {
            JsonObject tierObj = new JsonObject();
            tierObj.addProperty("health_floor", DragonSpeechConfig.minSurvivableHealth(tier));
            tierObj.addProperty("regen_multiplier", DragonSpeechConfig.regenMultiplier(tier));
            tierObj.addProperty("cost_multiplier", DragonSpeechConfig.costMultiplier(tier));
            tierObj.addProperty("structure_spacing", DragonSpeechConfig.structureSpacingMultiplier(tier));
            tuning.add(tier.name().toLowerCase(), tierObj);
        }
        root.add("difficulty_tuning", tuning);

        root.addProperty("allow_rebond_after_death", DragonSpeechConfig.allowRebondAfterDeath());
        root.addProperty("found_heart_stamina_min", DragonSpeechConfig.foundHeartStaminaMin());
        root.addProperty("found_heart_stamina_max", DragonSpeechConfig.foundHeartStaminaMax());
        root.addProperty("regen_multiplier_extra", DragonSpeechConfig.regenMultiplierExtra());
        root.addProperty("cost_multiplier_extra", DragonSpeechConfig.costMultiplierExtra());
        root.addProperty("backlash_severity_multiplier", DragonSpeechConfig.backlashSeverityMultiplier());
        root.addProperty("allow_pvp_mind_duels", DragonSpeechConfig.allowPvpMindDuels());
        root.addProperty("allow_control_of_players", DragonSpeechConfig.allowControlOfPlayers());
        root.addProperty("word_loot_chance_multiplier", DragonSpeechConfig.wordLootChanceMultiplier());
        root.addProperty("egg_hatch_speed_multiplier", DragonSpeechConfig.eggHatchSpeedMultiplier());
        root.addProperty("ai_compute_budget", DragonSpeechConfig.aiComputeBudget());

        // Sentience Editor (Server tab) - current overrides only, NOT the full entity list (the
        // client already has every registered EntityType locally via BuiltInRegistries, vanilla +
        // every installed mod's, so there's no need to send the whole registry over the wire - just
        // which entities currently have a non-default override, and what it is).
        JsonArray sentience = new JsonArray();
        for (SentienceConfig.Entry entry : SentienceConfig.currentOverrides()) {
            JsonObject obj = new JsonObject();
            obj.addProperty("id", entry.entityId());
            if (entry.tier() != null) {
                obj.addProperty("tier", entry.tier().getSerializedName());
            }
            if (entry.reactionPower() != null) {
                obj.addProperty("reaction_power", entry.reactionPower());
            }
            sentience.add(obj);
        }
        root.add("sentience_overrides", sentience);
        return root.toString();
    }
}
