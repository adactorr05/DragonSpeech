package com.dragonspeech.vocabulary;

import com.dragonspeech.word.DiscoveryMethod;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

/**
 * One word a player has discovered, plus the bookkeeping the grimoire UI
 * will eventually want: how they learned it, when, and whether they've
 * favorited it for quick access.
 */
public record KnownWordEntry(
    ResourceLocation wordId,
    DiscoveryMethod learnedVia,
    long discoveredAtGameTime,
    boolean favorited
) {
    public static final Codec<KnownWordEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        ResourceLocation.CODEC.fieldOf("word_id").forGetter(KnownWordEntry::wordId),
        DiscoveryMethod.CODEC.fieldOf("learned_via").forGetter(KnownWordEntry::learnedVia),
        Codec.LONG.fieldOf("discovered_at").forGetter(KnownWordEntry::discoveredAtGameTime),
        Codec.BOOL.optionalFieldOf("favorited", false).forGetter(KnownWordEntry::favorited)
    ).apply(instance, KnownWordEntry::new));

    public KnownWordEntry withFavorited(boolean newFavorited) {
        return new KnownWordEntry(wordId, learnedVia, discoveredAtGameTime, newFavorited);
    }
}
