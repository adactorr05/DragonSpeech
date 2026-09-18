package com.dragonspeech.client.mind;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Parses one TeamMindDuelSyncPayload JSON blob into plain fields - display-only, same spirit as MindDuelClientState. */
public final class TeamMindDuelClientState {

    public record Member(UUID id, String name, float focus, float maxFocus, float stamina, float maxStamina, boolean downed, boolean isSelf) {}

    public final boolean isAttacker;
    public final boolean ended;
    public final float linkStrength;
    public final Member attacker;
    public final List<Member> defenders;

    private TeamMindDuelClientState(boolean isAttacker, boolean ended, float linkStrength, Member attacker, List<Member> defenders) {
        this.isAttacker = isAttacker;
        this.ended = ended;
        this.linkStrength = linkStrength;
        this.attacker = attacker;
        this.defenders = defenders;
    }

    /** Null means "no active team duel" - the caller should close any open screen. */
    public static TeamMindDuelClientState parse(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonObject attackerObj = root.getAsJsonObject("attacker");
            Member attacker = new Member(null, attackerObj.get("name").getAsString(),
                attackerObj.get("focus").getAsFloat(), attackerObj.get("max_focus").getAsFloat(),
                attackerObj.get("stamina").getAsFloat(), attackerObj.get("max_stamina").getAsFloat(),
                false, false);

            List<Member> defenders = new ArrayList<>();
            JsonArray defenderArray = root.getAsJsonArray("defenders");
            for (var element : defenderArray) {
                JsonObject obj = element.getAsJsonObject();
                defenders.add(new Member(
                    UUID.fromString(obj.get("id").getAsString()), obj.get("name").getAsString(),
                    obj.get("focus").getAsFloat(), obj.get("max_focus").getAsFloat(),
                    obj.get("stamina").getAsFloat(), obj.get("max_stamina").getAsFloat(),
                    obj.get("downed").getAsBoolean(), obj.get("is_self").getAsBoolean()));
            }

            return new TeamMindDuelClientState(
                root.get("is_attacker").getAsBoolean(),
                root.get("ended").getAsBoolean(),
                root.get("link_strength").getAsFloat(),
                attacker, defenders);
        } catch (Exception ignored) {
            return null;
        }
    }
}
