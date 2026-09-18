package com.dragonspeech.mind;

import com.dragonspeech.network.DragonSpeechNetworking;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Pushes both a real JSON payload (for TeamMindDuelScreen, mirroring
 * MindDuelSyncHooks exactly) AND a short action-bar text line (so the
 * fight stays legible even for a player without the screen open, or
 * mid-action before the screen repaints) to every participant after a
 * team-duel action changes state.
 */
public final class TeamMindDuelSyncHooks {

    private TeamMindDuelSyncHooks() {}

    public static void pushSync(MinecraftServer server, TeamMindDuel duel) {
        String linkLine = String.format("Link Strength: %.0f%%", duel.linkStrength());

        ServerPlayer attacker = server.getPlayerList().getPlayer(duel.attackerId());
        if (attacker != null) {
            MindCombatant self = duel.attacker();
            attacker.displayClientMessage(Component.literal(String.format(
                "%s | Your Focus %.0f/%.0f, Stamina %.0f/%.0f | %d linked defenders",
                linkLine, self.focus(), self.maxFocus(), self.stamina(), self.maxStamina(), duel.defenders().size())), true);
            DragonSpeechNetworking.sendTeamMindDuelSync(attacker, toJson(server, duel, attacker.getUUID(), true).toString());
        }

        for (var entry : duel.defenders().entrySet()) {
            ServerPlayer member = server.getPlayerList().getPlayer(entry.getKey());
            if (member == null) {
                continue;
            }
            MindCombatant self = entry.getValue();
            String status = duel.isDowned(entry.getKey()) ? "DOWNED - awaiting revive" :
                String.format("Focus %.0f/%.0f, Stamina %.0f/%.0f", self.focus(), self.maxFocus(), self.stamina(), self.maxStamina());
            member.displayClientMessage(Component.literal(String.format("%s | You: %s", linkLine, status)), true);
            DragonSpeechNetworking.sendTeamMindDuelSync(member, toJson(server, duel, entry.getKey(), false).toString());
        }
    }

    public static void pushCleared(ServerPlayer player) {
        DragonSpeechNetworking.sendTeamMindDuelSync(player, "");
    }

    private static JsonObject toJson(MinecraftServer server, TeamMindDuel duel, UUID viewerId, boolean viewerIsAttacker) {
        JsonObject root = new JsonObject();
        root.addProperty("is_attacker", viewerIsAttacker);
        root.addProperty("link_strength", duel.linkStrength());
        root.addProperty("ended", duel.ended());

        JsonObject attackerObj = statObject(duel.attacker(), false);
        attackerObj.addProperty("name", playerName(server, duel.attackerId()));
        root.add("attacker", attackerObj);

        JsonArray defenders = new JsonArray();
        for (var entry : duel.defenders().entrySet()) {
            JsonObject memberObj = statObject(entry.getValue(), duel.isDowned(entry.getKey()));
            memberObj.addProperty("id", entry.getKey().toString());
            memberObj.addProperty("name", playerName(server, entry.getKey()));
            memberObj.addProperty("is_self", entry.getKey().equals(viewerId));
            defenders.add(memberObj);
        }
        root.add("defenders", defenders);
        return root;
    }

    private static String playerName(MinecraftServer server, UUID id) {
        ServerPlayer p = server.getPlayerList().getPlayer(id);
        return p != null ? p.getGameProfile().getName() : "unknown";
    }

    private static JsonObject statObject(MindCombatant combatant, boolean downed) {
        JsonObject obj = new JsonObject();
        obj.addProperty("focus", combatant.focus());
        obj.addProperty("max_focus", combatant.maxFocus());
        obj.addProperty("stamina", combatant.stamina());
        obj.addProperty("max_stamina", combatant.maxStamina());
        obj.addProperty("downed", downed);
        return obj;
    }
}
