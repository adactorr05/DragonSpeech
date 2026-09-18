package com.dragonspeech.client.grimoire;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

/** The local player's own learned skills, fed by SkillsSyncPayload. Display-only, same role ClientWardCache/ClientScarCache play for their own data. */
public final class ClientSkillsCache {

    public record SkillEntry(String label, boolean learned) {}

    // Order and labels here are the display list - see GrimoireScreen's skills panel. Keys must match the ones SkillSyncHooks writes.
    private static final String[] KEYS = {
        "sense_stamina", "gather_stamina", "can_sense_minds", "can_reach_out", "can_wall_mind", "can_read_thoughts", "can_bind_totally", "can_enchant"
    };
    private static final String[] LABELS = {
        "Sensing Stored Strength", "Drawing From Storage", "Sensing Minds Nearby",
        "Reaching Out With Your Mind", "Walling Your Own Mind", "Reading Surface Thoughts", "Binding Mind To Mind", "Enchanting Items"
    };

    private static List<SkillEntry> learned = new ArrayList<>();

    private ClientSkillsCache() {}

    public static void update(String skillsJson) {
        List<SkillEntry> parsed = new ArrayList<>();
        try {
            JsonObject obj = JsonParser.parseString(skillsJson).getAsJsonObject();
            for (int i = 0; i < KEYS.length; i++) {
                boolean has = obj.has(KEYS[i]) && obj.get(KEYS[i]).getAsBoolean();
                if (has) {
                    parsed.add(new SkillEntry(LABELS[i], true));
                }
            }
        } catch (Exception ignored) {
        }
        learned = parsed;
    }

    /** Only the ones actually learned - GrimoireScreen shows "None learned yet" itself when this is empty. */
    public static List<SkillEntry> get() {
        return learned;
    }
}
