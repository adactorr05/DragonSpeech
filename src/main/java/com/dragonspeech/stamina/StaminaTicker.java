package com.dragonspeech.stamina;

import com.dragonspeech.network.DragonSpeechNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Two jobs, both on a shared once-per-second pulse:
 *
 * 1. REGEN - stamina recovers passively over time. This never existed
 *    before now, which meant a drained caster stayed drained forever;
 *    with it, magic settles into a natural rhythm of exertion and
 *    recovery. Regen pauses while the player is starving (food empty) -
 *    the body has nothing to rebuild strength from.
 *
 * 2. SYNC - pushes current/max stamina to each player's client for the
 *    HUD bar, only when the value actually changed since the last push,
 *    so an idle full-stamina server sends nothing.
 */
public final class StaminaTicker {

    private static final int PULSE_INTERVAL_TICKS = 20;
    private static final float REGEN_PER_SECOND = 0.4f; // a full novice pool takes ~50s to recover - rest is a real cost

    private static final Map<UUID, Float> lastSyncedStamina = new HashMap<>();
    private static final Map<UUID, com.dragonspeech.storage.PlayerSkills> lastSyncedSkills = new HashMap<>();
    private static int counter = 0;

    private StaminaTicker() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(StaminaTicker::tick);
    }

    private static void tick(MinecraftServer server) {
        counter++;
        if (counter < PULSE_INTERVAL_TICKS) {
            return;
        }
        counter = 0;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PlayerMagicData magic = StaminaAccess.get(player);
            var scars = com.dragonspeech.scar.ScarAccess.get(player);
            float maxStamina = magic.maxStamina();

            boolean starving = player.getFoodData().getFoodLevel() <= 0;
            boolean regenDisabled = scars.has(com.dragonspeech.scar.ScarType.REGEN_DISABLED);
            boolean mindStopped = com.dragonspeech.mind.MindStaminaInterference.regenStopped(player, server.overworld().getGameTime());
            float mindRegenMultiplier = com.dragonspeech.mind.MindStaminaInterference.regenMultiplier(player, server.overworld().getGameTime());
            float regenCeiling = scars.find(com.dragonspeech.scar.ScarType.REGEN_CAP)
                .map(scar -> maxStamina * scar.magnitude())
                .orElse(maxStamina);

            // Blessing of Quiet Reserve ("kyrrafl") - +15% regen per level, worn only.
            int quietReserveLevel = com.dragonspeech.enchant.EquippedEnchantments.levelOf(player, "kyrrafl");
            float quietReserveMultiplier = 1f + 0.15f * quietReserveLevel;

            if (!starving && !regenDisabled && !mindStopped && magic.stamina() < Math.min(magic.maxStamina(), regenCeiling)) {
                magic = magic.withStamina(Math.min(regenCeiling, magic.stamina() + REGEN_PER_SECOND * mindRegenMultiplier
                        * com.dragonspeech.config.DragonSpeechConfig.regenMultiplier()
                        // Config GUI (Server tab) "Regen Multiplier" - independent of difficulty's own regenMultiplier() above, layered on top.
                        * com.dragonspeech.config.DragonSpeechConfig.regenMultiplierExtra()
                        * quietReserveMultiplier));
                StaminaAccess.set(player, magic);
            }

            // Curse of the Marked Brow ("ennisbol") - a permanent illr mark
            // while worn. Deliberately never auto-REMOVES the mark on
            // unequip - "lastingly... no unbinding lifts it" reads as the
            // mark itself being meant to stick once it's been applied at
            // all, not strictly tied to the item still being worn at this
            // exact instant.
            if (com.dragonspeech.enchant.EquippedEnchantments.has(player, "ennisbol") && !com.dragonspeech.engine.MarkRegistry.isBad(player)) {
                com.dragonspeech.engine.MarkRegistry.set(player, com.dragonspeech.engine.EntityMark.BAD);
            }

            Float lastSynced = lastSyncedStamina.get(player.getUUID());
            if (lastSynced == null || Math.abs(lastSynced - magic.stamina()) > 0.01f) {
                DragonSpeechNetworking.sendStaminaSync(player, magic.stamina(), magic.maxStamina());
                lastSyncedStamina.put(player.getUUID(), magic.stamina());
            }

            com.dragonspeech.storage.PlayerSkills skills = com.dragonspeech.storage.SkillsAccess.get(player);
            if (!skills.equals(lastSyncedSkills.get(player.getUUID()))) {
                com.google.gson.JsonObject obj = new com.google.gson.JsonObject();
                obj.addProperty("sense_stamina", skills.senseStamina());
                obj.addProperty("gather_stamina", skills.gatherStamina());
                obj.addProperty("can_sense_minds", skills.canSenseMinds());
                obj.addProperty("can_reach_out", skills.canReachOut());
                obj.addProperty("can_wall_mind", skills.canWallMind());
                obj.addProperty("can_read_thoughts", skills.canReadThoughts());
                obj.addProperty("can_bind_totally", skills.canBindTotally());
                obj.addProperty("can_enchant", skills.canEnchant());
                DragonSpeechNetworking.sendSkillsSync(player, obj.toString());
                lastSyncedSkills.put(player.getUUID(), skills);
            }
        }
        com.dragonspeech.mind.MindStaminaInterference.prune(server);
    }
}
