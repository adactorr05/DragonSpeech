package com.dragonspeech.client.construct;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The player's remembered phrases - spells saved from the blueprint for
 * later recall, persisted to config/dragonspeech_saved_spells.json so
 * they survive restarts. Client-side only: these are notes in YOUR book;
 * the server still verifies every word on every cast as always.
 */
public final class SavedSpells {

    /** `name` is the spell's WORDING snapshot from when it was saved - never changed by rename() anymore, so the row always shows what it actually casts. `description` is the separate, user-editable label - empty by default, shown after the dot. */
    public record SavedSpell(String name, List<String> wordIds, String description) {}

    private static final List<SavedSpell> spells = new ArrayList<>();
    private static boolean loaded = false;

    private SavedSpells() {}

    public static List<SavedSpell> all() {
        loadIfNeeded();
        return spells;
    }

    public static void save(String name, List<String> wordIds) {
        loadIfNeeded();
        spells.add(new SavedSpell(name, List.copyOf(wordIds), ""));
        persist();
    }

    public static void delete(int index) {
        loadIfNeeded();
        if (index >= 0 && index < spells.size()) {
            spells.remove(index);
            persist();
        }
    }

    /** Sets a saved spell's DESCRIPTION - the wording (name/words) is untouched, so you can always tell what it actually casts regardless of what you've labeled it. An empty description is allowed here (clears it back out), unlike the old rename-the-wording behavior this replaced. */
    public static void setDescription(int index, String newDescription) {
        loadIfNeeded();
        if (index < 0 || index >= spells.size()) {
            return;
        }
        SavedSpell existing = spells.get(index);
        spells.set(index, new SavedSpell(existing.name(), existing.wordIds(), newDescription.trim()));
        persist();
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("dragonspeech_saved_spells.json");
    }

    private static void loadIfNeeded() {
        if (loaded) {
            return;
        }
        loaded = true;
        try {
            if (!Files.exists(file())) {
                return;
            }
            JsonArray array = JsonParser.parseString(Files.readString(file())).getAsJsonArray();
            for (JsonElement element : array) {
                JsonObject obj = element.getAsJsonObject();
                List<String> ids = new ArrayList<>();
                for (JsonElement id : obj.getAsJsonArray("words")) {
                    ids.add(id.getAsString());
                }
                spells.add(new SavedSpell(obj.get("name").getAsString(), ids,
                    obj.has("description") ? obj.get("description").getAsString() : ""));
            }
        } catch (Exception ignored) {
        }
    }

    private static void persist() {
        try {
            JsonArray array = new JsonArray();
            for (SavedSpell spell : spells) {
                JsonObject obj = new JsonObject();
                obj.addProperty("name", spell.name());
                obj.addProperty("description", spell.description());
                JsonArray ids = new JsonArray();
                spell.wordIds().forEach(ids::add);
                obj.add("words", ids);
                array.add(obj);
            }
            Files.writeString(file(), new GsonBuilder().setPrettyPrinting().create().toJson(array));
        } catch (Exception ignored) {
        }
    }
}
