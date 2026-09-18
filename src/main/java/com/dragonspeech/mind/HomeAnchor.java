package com.dragonspeech.mind;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Where "heimbinda" last bound a player - a known place, kept until they
 * bind a new one. dimensionId is stored as a plain string (a
 * ResourceLocation's string form) rather than a ResourceKey directly,
 * since that's what actually round-trips cleanly through a simple Codec
 * without needing the full dynamic-registry machinery a ResourceKey codec
 * would drag in for a record this small.
 */
public record HomeAnchor(String dimensionId, double x, double y, double z, float yaw, boolean set) {

    public static final Codec<HomeAnchor> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.fieldOf("dimension").forGetter(HomeAnchor::dimensionId),
        Codec.DOUBLE.fieldOf("x").forGetter(HomeAnchor::x),
        Codec.DOUBLE.fieldOf("y").forGetter(HomeAnchor::y),
        Codec.DOUBLE.fieldOf("z").forGetter(HomeAnchor::z),
        Codec.FLOAT.fieldOf("yaw").forGetter(HomeAnchor::yaw),
        Codec.BOOL.fieldOf("set").forGetter(HomeAnchor::set)
    ).apply(instance, HomeAnchor::new));

    public static HomeAnchor none() {
        return new HomeAnchor("", 0, 0, 0, 0, false);
    }
}
