package com.dragonspeech.client.scar;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

/** The local player's own scar descriptions, fed by ScarSyncPayload. Display-only. */
public final class ClientScarCache {

    private static List<String> descriptions = new ArrayList<>();

    private ClientScarCache() {}

    public static void update(String descriptionsJson) {
        List<String> parsed = new ArrayList<>();
        try {
            JsonArray array = JsonParser.parseString(descriptionsJson).getAsJsonArray();
            for (JsonElement element : array) {
                parsed.add(element.getAsString());
            }
        } catch (Exception ignored) {
        }
        descriptions = parsed;
    }

    public static List<String> get() {
        return descriptions;
    }
}
