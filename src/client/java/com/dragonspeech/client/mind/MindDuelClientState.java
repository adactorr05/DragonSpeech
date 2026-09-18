package com.dragonspeech.client.mind;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses one MindDuelSyncPayload JSON blob into plain fields. Deliberately
 * dumb/display-only - the server is the only thing that ever decides
 * what these numbers mean.
 */
public final class MindDuelClientState {

    public record Bars(float focus, float maxFocus, float stamina, float maxStamina,
                        float willpower, float maxWillpower, float power, float maxPower,
                        float speed, float maxSpeed, float discipline) {}

    public record CrackView(int id, int variant, float x, float y, float openness) {}

    /** One barrier, from the current viewer's perspective - see myBarrier/theirBarrier below for which is which. */
    public record BarrierView(float integrity, List<CrackView> cracks) {}

    public final String phase;
    public final boolean isAttacker;
    public final float controlAdvantage;
    public final boolean pendingRoleChoice;
    public final boolean mindControlledSelf;
    /** null if the opponent's identity somehow wasn't resolvable server-side (e.g. they've since disconnected) - callers should treat that as "no true-name card available." */
    public final String opponentId;
    public final String opponentName;

    public final Bars self;
    public final Bars opponent;

    /** MY OWN barrier - what I have to mend, rendered gold client-side. Null outside Defense Breach. */
    public final BarrierView myBarrier;
    /** THEIR barrier - what I have to attack, rendered blue client-side. Null outside Defense Breach. */
    public final BarrierView theirBarrier;

    private MindDuelClientState(String phase, boolean isAttacker, float controlAdvantage, boolean pendingRoleChoice,
                                 boolean mindControlledSelf, String opponentId, String opponentName,
                                 Bars self, Bars opponent, BarrierView myBarrier, BarrierView theirBarrier) {
        this.phase = phase;
        this.isAttacker = isAttacker;
        this.controlAdvantage = controlAdvantage;
        this.pendingRoleChoice = pendingRoleChoice;
        this.mindControlledSelf = mindControlledSelf;
        this.opponentId = opponentId;
        this.opponentName = opponentName;
        this.self = self;
        this.opponent = opponent;
        this.myBarrier = myBarrier;
        this.theirBarrier = theirBarrier;
    }

    /** Null means "no active duel" - the caller should close any open duel screen. */
    public static MindDuelClientState parse(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            Bars self = parseBars(root.getAsJsonObject("self"));
            Bars opponent = parseBars(root.getAsJsonObject("opponent"));

            BarrierView myBarrier = root.has("my_barrier") ? parseBarrier(root.getAsJsonObject("my_barrier")) : null;
            BarrierView theirBarrier = root.has("their_barrier") ? parseBarrier(root.getAsJsonObject("their_barrier")) : null;

            return new MindDuelClientState(
                root.get("phase").getAsString(),
                root.get("is_attacker").getAsBoolean(),
                root.get("control_advantage").getAsFloat(),
                root.has("pending_role_choice") && root.get("pending_role_choice").getAsBoolean(),
                root.has("mind_controlled_self") && root.get("mind_controlled_self").getAsBoolean(),
                root.has("opponent_id") ? root.get("opponent_id").getAsString() : null,
                root.has("opponent_name") ? root.get("opponent_name").getAsString() : null,
                self, opponent, myBarrier, theirBarrier
            );
        } catch (Exception ignored) {
            return null;
        }
    }

    private static BarrierView parseBarrier(JsonObject obj) {
        List<CrackView> cracks = new ArrayList<>();
        if (obj.has("cracks")) {
            for (var element : obj.getAsJsonArray("cracks")) {
                JsonObject c = element.getAsJsonObject();
                float x = c.has("x") ? c.get("x").getAsFloat() : 0.5f;
                float y = c.has("y") ? c.get("y").getAsFloat() : 0.5f;
                cracks.add(new CrackView(c.get("id").getAsInt(), c.get("variant").getAsInt(), x, y, c.get("openness").getAsFloat()));
            }
        }
        return new BarrierView(obj.get("integrity").getAsFloat(), cracks);
    }

    private static Bars parseBars(JsonObject obj) {
        return new Bars(
            obj.get("focus").getAsFloat(), obj.get("max_focus").getAsFloat(),
            obj.get("stamina").getAsFloat(), obj.get("max_stamina").getAsFloat(),
            obj.get("willpower").getAsFloat(), obj.get("max_willpower").getAsFloat(),
            obj.get("power").getAsFloat(), obj.get("max_power").getAsFloat(),
            obj.get("speed").getAsFloat(), obj.get("max_speed").getAsFloat(),
            obj.get("discipline").getAsFloat()
        );
    }
}
