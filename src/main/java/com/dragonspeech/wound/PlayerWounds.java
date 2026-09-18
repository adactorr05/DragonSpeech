package com.dragonspeech.wound;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.EnumMap;
import java.util.Map;

/**
 * The typed hurt a player currently carries: how much of their missing
 * health belongs to each kind of wound. Deliberately NOT copied on
 * death - a fresh body carries no old wounds.
 */
public record PlayerWounds(Map<WoundType, Float> wounds) {

    public static final Codec<PlayerWounds> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.unboundedMap(WoundType.CODEC, Codec.FLOAT).optionalFieldOf("wounds", Map.of()).forGetter(PlayerWounds::wounds)
    ).apply(instance, PlayerWounds::new));

    public static PlayerWounds none() {
        return new PlayerWounds(Map.of());
    }

    public float get(WoundType type) {
        Float value = wounds.get(type);
        return value != null ? value : 0f;
    }

    public PlayerWounds withAdded(WoundType type, float amount) {
        Map<WoundType, Float> copy = new EnumMap<>(WoundType.class);
        copy.putAll(wounds);
        copy.merge(type, amount, Float::sum);
        return new PlayerWounds(Map.copyOf(copy));
    }

    public PlayerWounds withReduced(WoundType type, float amount) {
        Map<WoundType, Float> copy = new EnumMap<>(WoundType.class);
        copy.putAll(wounds);
        float newValue = Math.max(0f, get(type) - amount);
        if (newValue <= 0f) {
            copy.remove(type);
        } else {
            copy.put(type, newValue);
        }
        return new PlayerWounds(Map.copyOf(copy));
    }
}
