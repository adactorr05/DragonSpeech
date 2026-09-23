package com.dragonspeech.vocabulary;

import com.dragonspeech.event.DragonSpeechEvents;
import com.dragonspeech.word.DiscoveryMethod;
import com.dragonspeech.word.Word;
import com.dragonspeech.word.WordRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The one place anything in the mod should go to teach a player a word or
 * check what they already know - loot tables, NPC dialogue, the guessing
 * system (Phase 3), and the casting grid (Phase 2) should all call this
 * rather than touching VocabularyAccess directly, so prerequisite gating
 * and the discovery event always fire consistently no matter how a word
 * was learned.
 */
public final class VocabularyService {

    private VocabularyService() {}

    public enum LearnResult {
        LEARNED,
        ALREADY_KNOWN,
        PREREQUISITES_NOT_MET,
        UNKNOWN_WORD
    }

    /**
     * Attempts to teach the player a word. Fails closed on missing
     * prerequisites - a player can never skip straight to a synonym
     * ladder's precise form (e.g. "brenlokk") without already knowing
     * every word it's gated behind (e.g. "kyndla"), regardless of
     * discovery method. This applies even to guessing: correctly guessing
     * an ungated word's true name still won't unlock it early.
     */
    public static LearnResult learnWord(ServerPlayer player, ResourceLocation wordId, DiscoveryMethod method) {
        if (com.dragonspeech.wow.WordOfWordsKnowledge.WORD_ID.equals(wordId)) {
            if (com.dragonspeech.wow.WordOfWordsKnowledge.knows(player)) return LearnResult.ALREADY_KNOWN;
            return com.dragonspeech.wow.WordOfWordsKnowledge.learn(player, method) ? LearnResult.LEARNED : LearnResult.UNKNOWN_WORD;
        }
        Word word = WordRegistry.get(wordId);
        if (word == null) {
            return LearnResult.UNKNOWN_WORD;
        }
        if (word.discoveryMethod() == DiscoveryMethod.DANGER_WORD
                && method != DiscoveryMethod.DANGER_WORD && method != DiscoveryMethod.ADMIN_GRANTED) return LearnResult.UNKNOWN_WORD;

        PlayerVocabulary vocabulary = VocabularyAccess.get(player);
        if (vocabulary.knows(wordId)) {
            return LearnResult.ALREADY_KNOWN;
        }

        for (ResourceLocation prerequisite : word.prerequisiteWords()) {
            if (!vocabulary.knows(prerequisite)) {
                return LearnResult.PREREQUISITES_NOT_MET;
            }
        }

        KnownWordEntry entry = new KnownWordEntry(wordId, method, player.level().getGameTime(), false);
        VocabularyAccess.set(player, vocabulary.withWordLearned(entry));

        DragonSpeechEvents.WORD_DISCOVERED.invoker().onWordDiscovered(player, word, method);

        return LearnResult.LEARNED;
    }

    public static boolean knowsWord(ServerPlayer player, ResourceLocation wordId) {
        if (com.dragonspeech.wow.WordOfWordsKnowledge.WORD_ID.equals(wordId)) {
            return com.dragonspeech.wow.WordOfWordsKnowledge.knows(player);
        }
        return VocabularyAccess.get(player).knows(wordId);
    }

    /** Resolves every word ID the player knows against the live WordRegistry, keeping the ID alongside - needed for the network sync payload, which must send the client something it can echo back. */
    public static Map<ResourceLocation, Word> getKnownWordsWithIds(ServerPlayer player) {
        PlayerVocabulary vocabulary = VocabularyAccess.get(player);
        Map<ResourceLocation, Word> words = new LinkedHashMap<>();
        for (ResourceLocation id : vocabulary.knownWords().keySet()) {
            Word word = WordRegistry.get(id);
            if (word != null) {
                words.put(id, word);
            }
        }
        if (com.dragonspeech.wow.WordOfWordsKnowledge.knows(player)) {
            words.put(com.dragonspeech.wow.WordOfWordsKnowledge.WORD_ID,
                com.dragonspeech.wow.WordOfWordsKnowledge.dynamicWord(player.getServer()));
        }
        return words;
    }

    public static List<Word> getKnownWords(ServerPlayer player) {
        return new ArrayList<>(getKnownWordsWithIds(player).values());
    }

    /** A word's static data (Word) paired with this specific player's discovery bookkeeping (KnownWordEntry) - what the grimoire UI needs to show. */
    public record KnownWordView(ResourceLocation id, Word word, KnownWordEntry entry) {}

    public static List<KnownWordView> getKnownWordViews(ServerPlayer player) {
        PlayerVocabulary vocabulary = VocabularyAccess.get(player);
        List<KnownWordView> views = new ArrayList<>();
        for (Map.Entry<ResourceLocation, KnownWordEntry> mapEntry : vocabulary.knownWords().entrySet()) {
            Word word = WordRegistry.get(mapEntry.getKey());
            if (word != null) {
                views.add(new KnownWordView(mapEntry.getKey(), word, mapEntry.getValue()));
            }
        }
        if (com.dragonspeech.wow.WordOfWordsKnowledge.knows(player)) {
            KnownWordEntry entry = com.dragonspeech.wow.WordOfWordsKnowledge.knownEntry(player);
            if (entry != null) {
                views.add(new KnownWordView(com.dragonspeech.wow.WordOfWordsKnowledge.WORD_ID,
                    com.dragonspeech.wow.WordOfWordsKnowledge.dynamicWord(player.getServer()), entry));
            }
        }
        return views;
    }

    public static void setFavorite(ServerPlayer player, ResourceLocation wordId, boolean favorited) {
        PlayerVocabulary vocabulary = VocabularyAccess.get(player);
        VocabularyAccess.set(player, vocabulary.withFavoriteToggled(wordId, favorited));
    }

    /** Flips a word's favorite status. Silently does nothing if the player doesn't actually know that word (defends against a stale/tampered client id). */
    public static void toggleFavorite(ServerPlayer player, ResourceLocation wordId) {
        if (com.dragonspeech.wow.WordOfWordsKnowledge.WORD_ID.equals(wordId)) {
            com.dragonspeech.wow.WordOfWordsKnowledge.toggleFavorite(player);
            return;
        }
        PlayerVocabulary vocabulary = VocabularyAccess.get(player);
        KnownWordEntry existing = vocabulary.knownWords().get(wordId);
        if (existing == null) {
            return;
        }
        setFavorite(player, wordId, !existing.favorited());
    }
}
