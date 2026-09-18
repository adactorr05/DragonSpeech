package com.dragonspeech.wow;

import com.dragonspeech.DragonSpeech;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.WeakHashMap;

/** World-persistent reality rules altered through Change in the Word-of-Words GUI. */
public final class WordOfWordsRules {
    private static final Map<MinecraftServer, Float> COST_MULTIPLIER = new WeakHashMap<>();
    private WordOfWordsRules() {}

    public static synchronized float magicCostMultiplier(MinecraftServer server) {
        if (server == null) return 1f;
        return COST_MULTIPLIER.computeIfAbsent(server, WordOfWordsRules::load);
    }

    public static synchronized void setMagicCostMultiplier(MinecraftServer server, float multiplier) {
        if (server == null) return;
        float clamped = Math.max(0.25f, Math.min(4f, multiplier));
        COST_MULTIPLIER.put(server, clamped);
        persist(server, clamped);
    }

    private static Path file(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("data").resolve("dragonspeech_word_rules.json");
    }

    private static float load(MinecraftServer server) {
        try {
            Path path = file(server);
            if (!Files.exists(path)) return 1f;
            JsonObject obj = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            return obj.has("magic_cost_multiplier") ? obj.get("magic_cost_multiplier").getAsFloat() : 1f;
        } catch (Exception ex) {
            DragonSpeech.LOGGER.warn("Could not read Word-of-Words world rules; defaults restored.", ex);
            return 1f;
        }
    }

    private static void persist(MinecraftServer server, float multiplier) {
        try {
            Path path = file(server);
            Files.createDirectories(path.getParent());
            JsonObject obj = new JsonObject();
            obj.addProperty("magic_cost_multiplier", multiplier);
            Files.writeString(path, obj.toString());
        } catch (Exception ex) {
            DragonSpeech.LOGGER.error("Could not persist Word-of-Words world rules.", ex);
        }
    }
}
