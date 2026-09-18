package com.dragonspeech.scar;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.List;

public record PlayerScars(List<ActiveScar> scars) {

    public static final Codec<PlayerScars> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        ActiveScar.CODEC.listOf().optionalFieldOf("scars", List.of()).forGetter(PlayerScars::scars)
    ).apply(instance, PlayerScars::new));

    public static PlayerScars none() {
        return new PlayerScars(List.of());
    }

    public PlayerScars withAdded(ActiveScar scar) {
        List<ActiveScar> copy = new ArrayList<>(scars);
        copy.add(scar);
        return new PlayerScars(List.copyOf(copy));
    }

    public boolean has(ScarType type) {
        return scars.stream().anyMatch(s -> s.type() == type);
    }

    public java.util.Optional<ActiveScar> find(ScarType type) {
        return scars.stream().filter(s -> s.type() == type).findFirst();
    }
}
