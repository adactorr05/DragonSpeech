package com.dragonspeech.scar;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;

/**
 * A single lasting scar. `magnitude`'s meaning depends on `type`:
 *  COST_CEILING    -> the maximum single-cast cost still permitted
 *  REGEN_CAP       -> the fraction of max stamina regen will not pass
 *  everything else -> unused (0)
 * `cursedWord` is only set for CURSED_WORD. `lostWords` is only set for WORD_LOSS.
 */
public record ActiveScar(ScarType type, float magnitude, Optional<ResourceLocation> cursedWord, List<ResourceLocation> lostWords) {

    public static final Codec<ActiveScar> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        ScarType.CODEC.fieldOf("type").forGetter(ActiveScar::type),
        Codec.FLOAT.optionalFieldOf("magnitude", 0f).forGetter(ActiveScar::magnitude),
        ResourceLocation.CODEC.optionalFieldOf("cursed_word").forGetter(ActiveScar::cursedWord),
        ResourceLocation.CODEC.listOf().optionalFieldOf("lost_words", List.of()).forGetter(ActiveScar::lostWords)
    ).apply(instance, ActiveScar::new));

    /** A one-line human description, used both for the immediate "here is your scar" message and the grimoire listing. */
    public String describe() {
        return switch (type) {
            case WORD_LOSS -> "Lost words: " + lostWords.stream()
                .map(id -> {
                    var w = com.dragonspeech.word.WordRegistry.get(id);
                    return w != null ? w.trueName() : "???";
                })
                .reduce((a, b) -> a + ", " + b).orElse("(none)");
            case CHRONIC_PAIN -> "Chronic pain - the crossing left an ache that flares without warning.";
            case COST_CEILING -> String.format("Weakened reach - you can no longer cast anything costing more than %.0f.", magnitude);
            case REGEN_CAP -> String.format("Capped reserve - your stamina will not recover past %.0f%% of its maximum.", magnitude * 100f);
            case REGEN_DISABLED -> "Broken wellspring - your stamina no longer recovers on its own, ever.";
            case CURSED_WORD -> "Cursed word - " + cursedWord.map(id -> {
                var w = com.dragonspeech.word.WordRegistry.get(id);
                return w != null ? w.trueName() : "a word";
            }).orElse("a word") + " now wounds you whenever you speak it.";
        };
    }
}
