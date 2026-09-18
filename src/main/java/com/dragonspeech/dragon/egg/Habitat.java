package com.dragonspeech.dragon.egg;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.function.Function;

/**
 * A single hatching-condition check an egg's surroundings are scored
 * against - "how many points does this position earn toward hatching,
 * right now, for this habitat rule." Concept and the whole scoring idea
 * ported from Dragon Mounts Legacy's own Habitat system
 * (com.github.kay9.dragonmounts.dragon.egg.habitats.Habitat, GPL-3.0) -
 * per explicit direction ("I do like their hatching system so I will
 * use that").
 *
 * DISPATCH MECHANISM DELIBERATELY SIMPLER than the original: Dragon
 * Mounts Legacy registers each habitat type into an actual Mojang
 * dynamic registry (Habitat.REGISTRY_KEY), so its CODEC does a real
 * registry-backed dispatch. This project doesn't use that machinery
 * anywhere else (see DragonBreed's own doc for the same reasoning) -
 * and unlike breeds, habitat types genuinely can't be added by a
 * datapack alone anyway (getHabitatPoints() is compiled Java logic,
 * not data), so there's no real benefit to registry indirection here.
 * TYPE_CODECS is just a plain, fixed lookup table from a "type" string
 * to that type's own MapCodec - simpler, same practical result.
 */
public interface Habitat {

    int getHabitatPoints(Level level, BlockPos pos);

    String typeId();

    static <T extends Habitat> RecordCodecBuilder<T, Integer> withPoints(int defaultTo, Function<T, Integer> getter) {
        return Codec.INT.optionalFieldOf("points", defaultTo).forGetter(getter);
    }

    static <T extends Habitat> RecordCodecBuilder<T, Float> withMultiplier(float defaultTo, Function<T, Float> getter) {
        return Codec.FLOAT.optionalFieldOf("point_multiplier", defaultTo).forGetter(getter);
    }

    /** Populated by each concrete habitat type's own static initializer via register() - see the bottom of this file for the actual registration calls, kept together in one place rather than scattered so the full set of valid "type" values is easy to see at a glance. */
    Map<String, MapCodec<? extends Habitat>> TYPE_CODECS = new java.util.HashMap<>();

    static void register(String typeId, MapCodec<? extends Habitat> codec) {
        TYPE_CODECS.put(typeId, codec);
    }

    Codec<Habitat> CODEC = Codec.STRING.dispatch("type", Habitat::typeId, type -> {
        MapCodec<? extends Habitat> codec = TYPE_CODECS.get(type);
        if (codec == null) {
            // dispatch's decoder function is expected to throw on an
            // unrecognized key - Mojang's own codec machinery surfaces
            // this as a clear parse failure with the bad type string
            // included, rather than silently producing a broken
            // Habitat or requiring more intricate DataResult handling
            // here.
            throw new IllegalArgumentException("Unknown habitat type: " + type);
        }
        return codec;
    });
}
