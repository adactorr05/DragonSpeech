package com.dragonspeech.mind;

import com.dragonspeech.network.DragonSpeechNetworking;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Turns an ActiveMindDuel into the JSON blob MindDuelSyncPayload carries,
 * and pushes it to whichever participant(s) are actual players (a mob
 * defender obviously gets nothing sent to it). Call this after every
 * action that changes duel state - MindDuelActionService and
 * ContactResolver both do.
 */
public final class MindDuelSyncHooks {

    private MindDuelSyncHooks() {}

    public static void pushSync(MinecraftServer server, ActiveMindDuel duel) {
        ServerPlayer attacker = server.getPlayerList().getPlayer(duel.attackerId());
        ServerPlayer defender = server.getPlayerList().getPlayer(duel.defenderId());

        if (attacker != null) {
            DragonSpeechNetworking.sendMindDuelSync(attacker, toJson(server, duel, true).toString());
        }
        if (defender != null) {
            DragonSpeechNetworking.sendMindDuelSync(defender, toJson(server, duel, false).toString());
        }
    }

    /** Tells a specific player's client to close its duel screen, e.g. after they log back in mid-duel-that-no-longer-exists. */
    public static void pushCleared(ServerPlayer player) {
        DragonSpeechNetworking.sendMindDuelSync(player, "");
    }

    private static JsonObject toJson(MinecraftServer server, ActiveMindDuel duel, boolean viewerIsAttacker) {
        MindCombatant self = viewerIsAttacker ? duel.attacker() : duel.defender();
        MindCombatant opponent = viewerIsAttacker ? duel.defender() : duel.attacker();

        JsonObject root = new JsonObject();
        root.addProperty("phase", duel.phase().getSerializedName());
        root.addProperty("is_attacker", viewerIsAttacker);
        root.addProperty("control_advantage", viewerIsAttacker ? duel.controlAdvantage() : -duel.controlAdvantage());
        root.addProperty("pending_role_choice", duel.pendingRoleChoice() && !viewerIsAttacker);
        root.addProperty("mind_controlled_self", !viewerIsAttacker && MindControlService.isControlled(duel.defenderId()));

        // Added specifically so the client can offer a "True Name" card
        // during OCCUPIED_MIND without needing a second round-trip just
        // to find out who it's even connected to - see MindDuelScreen's
        // rebuildCommandButtons().
        java.util.UUID opponentId = viewerIsAttacker ? duel.defenderId() : duel.attackerId();
        root.addProperty("opponent_id", opponentId.toString());
        var opponentEntity = EntityLookup.byUUID(server, opponentId);
        root.addProperty("opponent_name", opponentEntity != null ? opponentEntity.getName().getString() : "Unknown");

        root.add("self", statObject(self));
        root.add("opponent", statObject(opponent));

        if (duel.phase() == DuelPhase.DEFENSE_BREACH) {
            // Perspective-relative, matching the current design: every
            // viewer sees "my_barrier" (their own, to mend - rendered
            // gold client-side) and "their_barrier" (the opponent's, to
            // attack - rendered blue), regardless of which duel role
            // they actually play. An attacker viewing this sees
            // my_barrier = the attacker's own barrier; a defender
            // viewing it sees my_barrier = the defender's own barrier -
            // same JSON shape either way, just swapped underneath.
            BreachState myBreach = viewerIsAttacker ? duel.attackerBreach() : duel.defenderBreach();
            BreachState theirBreach = viewerIsAttacker ? duel.defenderBreach() : duel.attackerBreach();
            float myIntegrity = viewerIsAttacker ? duel.attackerBarrierIntegrity() : duel.defenderBarrierIntegrity();
            float theirIntegrity = viewerIsAttacker ? duel.defenderBarrierIntegrity() : duel.attackerBarrierIntegrity();

            root.add("my_barrier", barrierObject(myIntegrity, myBreach));
            root.add("their_barrier", barrierObject(theirIntegrity, theirBreach));
        }
        return root;
    }

    private static JsonObject barrierObject(float integrity, BreachState breach) {
        JsonObject obj = new JsonObject();
        obj.addProperty("integrity", integrity);
        obj.add("cracks", cracksArray(breach));
        return obj;
    }

    private static JsonArray cracksArray(BreachState breach) {
        JsonArray array = new JsonArray();
        for (Crack crack : breach.cracks()) {
            JsonObject obj = new JsonObject();
            obj.addProperty("id", crack.id());
            obj.addProperty("variant", crack.variantId());
            obj.addProperty("x", crack.x());
            obj.addProperty("y", crack.y());
            obj.addProperty("openness", crack.openness());
            array.add(obj);
        }
        return array;
    }

    private static JsonObject statObject(MindCombatant combatant) {
        JsonObject obj = new JsonObject();
        obj.addProperty("focus", combatant.focus());
        obj.addProperty("max_focus", combatant.maxFocus());
        obj.addProperty("stamina", combatant.stamina());
        obj.addProperty("max_stamina", combatant.maxStamina());
        obj.addProperty("willpower", combatant.willpower());
        obj.addProperty("max_willpower", combatant.maxWillpower());
        obj.addProperty("power", combatant.power());
        obj.addProperty("max_power", combatant.maxPower());
        obj.addProperty("speed", combatant.speed());
        obj.addProperty("max_speed", combatant.maxSpeed());
        obj.addProperty("discipline", combatant.discipline());
        return obj;
    }
}
