package com.dragonspeech.dragon.egg.habitats;

import com.dragonspeech.dragon.egg.Habitat;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Ported from Dragon Mounts Legacy (GPL-3.0) - a composite habitat
 * where ALL nested conditions must be satisfied (any one scoring zero
 * fails the whole thing), not just summed independently like a plain
 * list of habitats would be.
 */
public record PickyHabitat(List<Habitat> habitats) implements Habitat {
    public static final String TYPE = "picky";
    public static final MapCodec<PickyHabitat> CODEC = Habitat.CODEC
            .listOf()
            .fieldOf("required_habitats")
            .xmap(PickyHabitat::new, PickyHabitat::habitats);

    static {
        Habitat.register(TYPE, CODEC);
    }

    @Override
    public int getHabitatPoints(Level level, BlockPos pos) {
        int points = 0;
        for (var habitat : habitats) {
            int i = habitat.getHabitatPoints(level, pos);
            if (i == 0) {
                return 0;
            }
            points += i;
        }
        return points;
    }

    @Override
    public String typeId() {
        return TYPE;
    }
}
