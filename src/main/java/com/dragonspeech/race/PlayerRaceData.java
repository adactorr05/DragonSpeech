package com.dragonspeech.race;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

/** Unset until the player chooses - see RaceCommands. */
public record PlayerRaceData(Optional<RaceType> race) {

    public static final Codec<PlayerRaceData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        RaceType.CODEC.optionalFieldOf("race").forGetter(PlayerRaceData::race)
    ).apply(instance, PlayerRaceData::new));

    public static PlayerRaceData unset() {
        return new PlayerRaceData(Optional.empty());
    }
}
