package com.dragonspeech.wow;

import com.dragonspeech.DragonSpeech;
import com.dragonspeech.event.DragonSpeechEvents;
import com.dragonspeech.network.VocabularySyncHooks;
import com.dragonspeech.vocabulary.KnownWordEntry;
import com.dragonspeech.word.DiscoveryMethod;
import com.dragonspeech.word.Domain;
import com.dragonspeech.word.RiskTier;
import com.dragonspeech.word.Word;
import com.dragonspeech.word.WordCategory;
import com.dragonspeech.word.WordHashing;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Per-world/player knowledge state for the generated Word of Words.
 *
 * The Word is deliberately not a datapack WordRegistry entry: its plaintext is
 * generated per world and must never leak into ordinary world-discovery pools.
 * Instead this service exposes it to the normal construction/grimoire/casting
 * systems only for players who have actually discovered the CURRENT generation.
 */
public final class WordOfWordsKnowledge {
    public static final ResourceLocation WORD_ID = DragonSpeech.id("word_of_words");
    private static final Map<MinecraftServer, Map<UUID, Entry>> CACHE = new WeakHashMap<>();
    private static final float SENTENCE_AUTHORITY_COST_MULTIPLIER = 0.35f;

    private record Entry(int generation, boolean anchored, DiscoveryMethod learnedVia, long learnedAt, boolean favorited) {}

    private WordOfWordsKnowledge() {}

    public static boolean knows(ServerPlayer player) {
        Entry e = entries(player.getServer()).get(player.getUUID());
        return e != null && e.generation() == WowPhraseState.generation(player.getServer());
    }

    public static boolean isAnchored(ServerPlayer player) {
        Entry e = entries(player.getServer()).get(player.getUUID());
        return e != null && e.anchored() && e.generation() == WowPhraseState.generation(player.getServer());
    }

    public static boolean learn(ServerPlayer player, DiscoveryMethod method) {
        MinecraftServer server = player.getServer();
        if (server == null) return false;
        Map<UUID, Entry> map = entries(server);
        Entry old = map.get(player.getUUID());
        int generation = WowPhraseState.generation(server);
        if (old != null && old.generation() == generation) return false;
        boolean anchored = old != null && old.anchored();
        boolean favorite = old != null && old.favorited();
        Entry next = new Entry(generation, anchored, method, player.level().getGameTime(), favorite);
        map.put(player.getUUID(), next);
        persist(server, map);
        DragonSpeechEvents.WORD_DISCOVERED.invoker().onWordDiscovered(player, dynamicWord(server), method);
        VocabularySyncHooks.pushSync(player);
        return true;
    }

    /** Paid Word-of-Words action: the player's memory follows future magical reshuffles. */
    public static void bindToMemory(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null || !knows(player)) return;
        Map<UUID, Entry> map = entries(server);
        Entry old = map.get(player.getUUID());
        map.put(player.getUUID(), new Entry(old.generation(), true, old.learnedVia(), old.learnedAt(), old.favorited()));
        persist(server, map);
        VocabularySyncHooks.pushSync(player);
    }

    public static void toggleFavorite(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null || !knows(player)) return;
        Map<UUID, Entry> map = entries(server);
        Entry old = map.get(player.getUUID());
        map.put(player.getUUID(), new Entry(old.generation(), old.anchored(), old.learnedVia(), old.learnedAt(), !old.favorited()));
        persist(server, map);
        VocabularySyncHooks.pushSync(player);
    }

    /** Admin/reset mechanic: completely removes current/historical knowledge and any memory binding. */
    public static void forget(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        Map<UUID, Entry> map = entries(server);
        map.remove(player.getUUID());
        persist(server, map);
        VocabularySyncHooks.pushSync(player);
    }

    public static KnownWordEntry knownEntry(ServerPlayer player) {
        Entry e = entries(player.getServer()).get(player.getUUID());
        if (e == null || e.generation() != WowPhraseState.generation(player.getServer())) return null;
        return new KnownWordEntry(WORD_ID, e.learnedVia(), e.learnedAt(), e.favorited());
    }

    /**
     * Called AFTER WowPhraseState increments generation.
     * Magical reshuffle preserves anchored memories; admin reshuffle is a hard reset.
     */
    public static synchronized void onReshuffle(MinecraftServer server, boolean preserveAnchored) {
        Map<UUID, Entry> map = entries(server);
        int newGeneration = WowPhraseState.generation(server);
        Map<UUID, Entry> next = new HashMap<>();
        for (var item : map.entrySet()) {
            Entry e = item.getValue();
            if (preserveAnchored && e.anchored()) {
                next.put(item.getKey(), new Entry(newGeneration, true, e.learnedVia(), e.learnedAt(), e.favorited()));
            } else {
                // Deliberately retain the old generation as historical bookkeeping;
                // knows() becomes false immediately. Admin reset also severs the anchor.
                next.put(item.getKey(), new Entry(e.generation(), preserveAnchored && e.anchored(), e.learnedVia(), e.learnedAt(), e.favorited()));
            }
        }
        CACHE.put(server, next);
        persist(server, next);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            VocabularySyncHooks.pushSync(player);
        }
    }

    public static Word dynamicWord(MinecraftServer server) {
        String secret = WowPhraseState.current(server);
        return new Word(
            secret,
            WordHashing.hash(secret),
            "the Ancient Language itself; authority over the words that bind magic and reality",
            WordCategory.MODIFIER,
            Domain.TRUTH,
            1.0f,
            Optional.of(WORD_ID),
            List.of(),
            DiscoveryMethod.GUESSED,
            RiskTier.CATASTROPHIC,
            false,
            0.0f,
            false,
            Optional.empty(),
            0.0f,
            Optional.empty(),
            Optional.empty(),
            false,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            false,
            1,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            false,
            0.0f
        );
    }

    public static boolean isDynamicWord(Word word) {
        return word != null && word.synonymGroup().filter(WORD_ID::equals).isPresent();
    }

    /** A Word-of-Words token used inside a normal sentence makes that working dramatically cheaper. */
    public static float sentenceCostMultiplier(com.dragonspeech.spell.SpellComposition composition) {
        return composition.words().stream().anyMatch(WordOfWordsKnowledge::isDynamicWord)
            ? SENTENCE_AUTHORITY_COST_MULTIPLIER : 1.0f;
    }

    private static synchronized Map<UUID, Entry> entries(MinecraftServer server) {
        return CACHE.computeIfAbsent(server, WordOfWordsKnowledge::load);
    }

    private static Path file(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("data").resolve("dragonspeech_word_of_words_knowledge.json");
    }

    private static Map<UUID, Entry> load(MinecraftServer server) {
        Map<UUID, Entry> out = new HashMap<>();
        try {
            Path path = file(server);
            if (!Files.exists(path)) return out;
            JsonObject root = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            JsonObject players = root.has("players") ? root.getAsJsonObject("players") : new JsonObject();
            for (String key : players.keySet()) {
                try {
                    UUID id = UUID.fromString(key);
                    JsonObject obj = players.getAsJsonObject(key);
                    int generation = obj.has("generation") ? obj.get("generation").getAsInt() : 0;
                    boolean anchored = obj.has("anchored") && obj.get("anchored").getAsBoolean();
                    DiscoveryMethod method = DiscoveryMethod.GUESSED;
                    if (obj.has("learned_via")) {
                        try { method = DiscoveryMethod.valueOf(obj.get("learned_via").getAsString().toUpperCase(java.util.Locale.ROOT)); }
                        catch (Exception ignored) {}
                    }
                    long learnedAt = obj.has("learned_at") ? obj.get("learned_at").getAsLong() : 0L;
                    boolean favorite = obj.has("favorited") && obj.get("favorited").getAsBoolean();
                    out.put(id, new Entry(generation, anchored, method, learnedAt, favorite));
                } catch (Exception ignored) {}
            }
        } catch (Exception ex) {
            DragonSpeech.LOGGER.warn("Could not read Word-of-Words player knowledge; starting with none.", ex);
        }
        return out;
    }

    private static void persist(MinecraftServer server, Map<UUID, Entry> map) {
        try {
            Path path = file(server);
            Files.createDirectories(path.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("format", 1);
            JsonObject players = new JsonObject();
            for (var item : map.entrySet()) {
                Entry e = item.getValue();
                JsonObject obj = new JsonObject();
                obj.addProperty("generation", e.generation());
                obj.addProperty("anchored", e.anchored());
                obj.addProperty("learned_via", e.learnedVia().getSerializedName());
                obj.addProperty("learned_at", e.learnedAt());
                obj.addProperty("favorited", e.favorited());
                players.add(item.getKey().toString(), obj);
            }
            root.add("players", players);
            Files.writeString(path, root.toString());
        } catch (Exception ex) {
            DragonSpeech.LOGGER.error("Could not persist Word-of-Words player knowledge.", ex);
        }
    }
}
