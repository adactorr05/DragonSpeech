package com.dragonspeech.ward;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.List;

public record PlayerWards(List<ActiveWard> wards) {

    public static final Codec<PlayerWards> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        ActiveWard.CODEC.listOf().optionalFieldOf("wards", List.of()).forGetter(PlayerWards::wards)
    ).apply(instance, PlayerWards::new));

    public static PlayerWards empty() {
        return new PlayerWards(List.of());
    }

    public PlayerWards withAdded(ActiveWard ward) {
        List<ActiveWard> copy = new ArrayList<>(wards);
        copy.add(ward);
        return new PlayerWards(List.copyOf(copy));
    }

    public PlayerWards withReplaced(ActiveWard updated) {
        List<ActiveWard> copy = new ArrayList<>();
        for (ActiveWard w : wards) {
            copy.add(w.id().equals(updated.id()) ? updated : w);
        }
        return new PlayerWards(List.copyOf(copy));
    }

    /** Drops any broken wards - called after every trigger check. */
    public PlayerWards withBrokenRemoved() {
        List<ActiveWard> copy = new ArrayList<>();
        for (ActiveWard w : wards) {
            if (!w.isBroken()) {
                copy.add(w);
            }
        }
        return new PlayerWards(List.copyOf(copy));
    }

    public PlayerWards withRemoved(java.util.UUID wardId) {
        List<ActiveWard> copy = new ArrayList<>();
        for (ActiveWard w : wards) {
            if (!w.id().equals(wardId)) {
                copy.add(w);
            }
        }
        return new PlayerWards(List.copyOf(copy));
    }
}
