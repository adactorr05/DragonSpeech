package com.dragonspeech.client.ward;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

/**
 * The local player's own wards, fed by WardSyncPayload. Display-only.
 *
 * WardVisual now carries the real current/max energy and whether it's
 * stamina-bound, not just a 0-1 fraction - per explicit direction ("in
 * the grimoire screen, it should also show what the durability on my
 * ward is... 36/36 and not just 36... Connected to Stamina"). fraction()
 * is kept exactly as before for WardRingRenderer, which only ever reads
 * that one field - no changes needed there.
 */
public final class ClientWardCache {

    public record WardVisual(String type, float fraction, float remaining, float max, boolean staminaBound) {}

    private static List<WardVisual> wards = new ArrayList<>();

    private ClientWardCache() {}

    public static void update(String wardsJson) {
        List<WardVisual> parsed = new ArrayList<>();
        try {
            JsonArray array = JsonParser.parseString(wardsJson).getAsJsonArray();
            for (JsonElement element : array) {
                var obj = element.getAsJsonObject();
                parsed.add(new WardVisual(
                    obj.get("type").getAsString(),
                    obj.get("fraction").getAsFloat(),
                    obj.has("remaining") ? obj.get("remaining").getAsFloat() : 0f,
                    obj.has("max") ? obj.get("max").getAsFloat() : 0f,
                    obj.has("stamina_bound") && obj.get("stamina_bound").getAsBoolean()
                ));
            }
        } catch (Exception ignored) {
        }
        wards = parsed;
    }

    public static List<WardVisual> get() {
        return wards;
    }
}
