package com.dragonspeech.wow;

import com.dragonspeech.DragonSpeech;
import com.dragonspeech.word.WordRegistry;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Per-world state for the Word of Words.
 *
 * This intentionally is NOT a static WordRegistry/datapack entry. It must never
 * leak through tablets, scholars, datapacks, or resource dumps. Players may still
 * discover the current generated word by an exact guess or by speaking it in chat.
 * Each save gets one generated 7-15 letter Ancient-Language-style word persisted
 * inside that save directory, and reshuffling replaces its plaintext generation.
 */
public final class WowPhraseState {

    private static final SecureRandom RANDOM = new SecureRandom();
    private record State(String word, int generation) {}
    private static final Map<MinecraftServer, State> CACHE = new WeakHashMap<>();

    // Phonotactics are deliberately assembled from sound-shapes already common
    // in Dragon Speech: hard/soft consonants, liquid clusters, Norse-like
    // vowels and endings. Generation is random, but never arbitrary keyboard
    // noise and never copies an ordinary registered word.
    private static final String[] ONSETS = {
        "b", "d", "f", "g", "h", "j", "k", "l", "m", "n", "r", "s", "t", "v",
        "br", "dr", "fj", "fr", "gr", "hr", "kr", "sk", "st", "sv", "th", "vr"
    };
    private static final String[] NUCLEI = {
        "a", "e", "i", "o", "u", "y", "ae", "ei", "ia", "io", "au"
    };
    private static final String[] CODAS = {
        "", "d", "f", "g", "k", "l", "m", "n", "r", "s", "t", "v",
        "nd", "ng", "ld", "rd", "rk", "rn", "st", "th"
    };

    private WowPhraseState() {}

    public static synchronized String current(MinecraftServer server) {
        State cached = CACHE.get(server);
        if (cached != null && !cached.word().isBlank()) return cached.word();

        State loaded = load(server);
        if (loaded == null || !validSecret(loaded.word())) {
            loaded = new State(generate(), 1);
            persist(server, loaded);
        }
        CACHE.put(server, loaded);
        return loaded.word();
    }

    public static synchronized int generation(MinecraftServer server) {
        current(server);
        return CACHE.get(server).generation();
    }

    /** Admin hard reset: nobody keeps knowledge, even if it had been memory-bound. */
    public static synchronized String reshuffle(MinecraftServer server) {
        return reshuffleInternal(server, false);
    }

    /** Word-powered change: players who paid to bind the Word to memory follow the new generation. */
    public static synchronized String reshuffleByWord(MinecraftServer server) {
        return reshuffleInternal(server, true);
    }

    private static String reshuffleInternal(MinecraftServer server, boolean preserveAnchored) {
        String previous = current(server);
        int nextGeneration = generation(server) + 1;
        String next;
        do {
            next = generate();
        } while (next.equalsIgnoreCase(previous));
        State state = new State(next, nextGeneration);
        CACHE.put(server, state);
        persist(server, state);
        WordOfWordsService.invalidateAll(server);
        WordOfWordsKnowledge.onReshuffle(server, preserveAnchored);
        return next;
    }

    public static boolean matches(MinecraftServer server, String raw) {
        if (server == null || raw == null) return false;
        String trimmed = raw.trim().toLowerCase(Locale.ROOT);
        return !trimmed.contains(" ") && trimmed.equals(current(server));
    }

    private static String generate() {
        for (int attempt = 0; attempt < 256; attempt++) {
            int target = 7 + RANDOM.nextInt(9); // 7..15 inclusive
            StringBuilder out = new StringBuilder(16);
            while (out.length() < target) {
                out.append(ONSETS[RANDOM.nextInt(ONSETS.length)]);
                out.append(NUCLEI[RANDOM.nextInt(NUCLEI.length)]);
                if (RANDOM.nextFloat() < 0.78f) {
                    out.append(CODAS[RANDOM.nextInt(CODAS.length)]);
                }
            }
            if (out.length() > 15) out.setLength(15);
            String candidate = out.toString().toLowerCase(Locale.ROOT);
            if (candidate.length() >= 7 && candidate.length() <= 15 && !registeredTrueName(candidate)) {
                return candidate;
            }
        }
        // Cryptographically-random fallback is still pronounceable enough to
        // satisfy the sound rules and cannot collide with a normal word.
        return "tharveldr" + Integer.toHexString(RANDOM.nextInt(0x10000));
    }

    private static boolean registeredTrueName(String candidate) {
        return WordRegistry.getAllWords().values().stream()
            .anyMatch(word -> word.trueName().equalsIgnoreCase(candidate));
    }

    private static boolean validSecret(String word) {
        return word != null && word.matches("[a-z]{7,15}") && !registeredTrueName(word);
    }

    private static Path file(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT)
            .resolve("data")
            .resolve("dragonspeech_word_of_words.json");
    }

    private static State load(MinecraftServer server) {
        try {
            Path path = file(server);
            if (!Files.exists(path)) return null;
            JsonObject obj = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            String word = obj.has("word") ? obj.get("word").getAsString().toLowerCase(Locale.ROOT) : null;
            int generation = obj.has("generation") ? Math.max(1, obj.get("generation").getAsInt()) : 1;
            return word == null ? null : new State(word, generation);
        } catch (Exception ex) {
            DragonSpeech.LOGGER.warn("Could not read this world's Word of Words; generating a replacement.", ex);
            return null;
        }
    }

    private static void persist(MinecraftServer server, State state) {
        try {
            Path path = file(server);
            Files.createDirectories(path.getParent());
            JsonObject obj = new JsonObject();
            obj.addProperty("word", state.word());
            obj.addProperty("generation", state.generation());
            obj.addProperty("format", 3);
            Files.writeString(path, obj.toString());
        } catch (Exception ex) {
            DragonSpeech.LOGGER.error("Could not persist this world's Word of Words.", ex);
        }
    }
}
