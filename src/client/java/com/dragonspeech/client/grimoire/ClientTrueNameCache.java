package com.dragonspeech.client.grimoire;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** The local player's own true-name progress list, fed by TrueNameProgressSyncPayload - the Grimoire's "true names" tab reads this directly. */
public final class ClientTrueNameCache {

    public record Entry(UUID targetId, String targetName, String collectedLetters, boolean solved, String solvedName) {}

    private static List<Entry> entries = new ArrayList<>();

    private ClientTrueNameCache() {}

    public static void update(String progressJson) {
        List<Entry> parsed = new ArrayList<>();
        try {
            JsonObject root = JsonParser.parseString(progressJson).getAsJsonObject();
            JsonArray array = root.getAsJsonArray("entries");
            for (var el : array) {
                JsonObject obj = el.getAsJsonObject();
                UUID targetId = UUID.fromString(obj.get("target_id").getAsString());
                String targetName = obj.get("target_name").getAsString();
                String collectedLetters = obj.get("collected_letters").getAsString();
                boolean solved = obj.get("solved").getAsBoolean();
                String solvedName = obj.has("solved_name") ? obj.get("solved_name").getAsString() : null;
                parsed.add(new Entry(targetId, targetName, collectedLetters, solved, solvedName));
            }
        } catch (Exception ignored) {
        }
        entries = parsed;
    }

    public static List<Entry> get() {
        return entries;
    }
}
