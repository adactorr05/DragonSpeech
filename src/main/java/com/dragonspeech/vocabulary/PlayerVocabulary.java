package com.dragonspeech.vocabulary;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * A player's full known-word set. Immutable like everything else in this
 * project's data layer - every mutation returns a new instance, which
 * VocabularyAccess then writes back through the attachment.
 */
public record PlayerVocabulary(Map<ResourceLocation, KnownWordEntry> knownWords) {

    public static final Codec<PlayerVocabulary> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.unboundedMap(ResourceLocation.CODEC, KnownWordEntry.CODEC)
            .optionalFieldOf("known_words", Map.of())
            .forGetter(PlayerVocabulary::knownWords)
    ).apply(instance, PlayerVocabulary::new));

    public static PlayerVocabulary empty() {
        return new PlayerVocabulary(Map.of());
    }

    public boolean knows(ResourceLocation wordId) {
        return knownWords.containsKey(wordId);
    }

    /** Severs a word from this vocabulary. Words are near-unlosable by design - the ONLY legitimate callers are the sanctioned loss mechanics: resurrection scars, true-name coercion (future), and admin resets. Never call this casually. */
    public PlayerVocabulary withWordRemoved(ResourceLocation wordId) {
        java.util.Map<ResourceLocation, KnownWordEntry> copy = new java.util.HashMap<>(knownWords);
        copy.remove(wordId);
        return new PlayerVocabulary(java.util.Map.copyOf(copy));
    }

    public PlayerVocabulary withWordLearned(KnownWordEntry entry) {
        Map<ResourceLocation, KnownWordEntry> copy = new HashMap<>(knownWords);
        copy.put(entry.wordId(), entry);
        return new PlayerVocabulary(Map.copyOf(copy));
    }

    public PlayerVocabulary withFavoriteToggled(ResourceLocation wordId, boolean favorited) {
        KnownWordEntry existing = knownWords.get(wordId);
        if (existing == null) {
            return this;
        }
        Map<ResourceLocation, KnownWordEntry> copy = new HashMap<>(knownWords);
        copy.put(wordId, existing.withFavorited(favorited));
        return new PlayerVocabulary(Map.copyOf(copy));
    }
}
