package com.dragonspeech.growth;

import com.dragonspeech.word.Domain;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.EnumMap;
import java.util.Map;

/**
 * How practiced a player is in each domain. Growing this is what makes
 * a veteran caster's spells feel controlled and cheap where a beginner's
 * feel dangerous and expensive for the same task - see AttunementService
 * for how this actually discounts cost.
 */
public record PlayerAttunementData(Map<Domain, Float> attunement) {

    public static final float MAX_ATTUNEMENT = 100f;

    public static final Codec<PlayerAttunementData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.unboundedMap(Domain.CODEC, Codec.FLOAT).optionalFieldOf("attunement", Map.of()).forGetter(PlayerAttunementData::attunement)
    ).apply(instance, PlayerAttunementData::new));

    public static PlayerAttunementData empty() {
        return new PlayerAttunementData(Map.of());
    }

    public float get(Domain domain) {
        Float value = attunement.get(domain);
        return value != null ? value : 0f;
    }

    public PlayerAttunementData withIncreased(Domain domain, float amount) {
        Map<Domain, Float> copy = new EnumMap<>(Domain.class);
        copy.putAll(attunement);
        float newValue = Math.min(MAX_ATTUNEMENT, get(domain) + amount);
        copy.put(domain, newValue);
        return new PlayerAttunementData(Map.copyOf(copy));
    }
}
