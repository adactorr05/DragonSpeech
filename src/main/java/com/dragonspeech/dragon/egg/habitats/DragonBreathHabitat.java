package com.dragonspeech.dragon.egg.habitats;

import com.dragonspeech.dragon.egg.Habitat;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/** Ported from Dragon Mounts Legacy (GPL-3.0) - checks for a nearby dragon-breath area-effect cloud. */
public enum DragonBreathHabitat implements Habitat {
    INSTANCE;

    public static final String TYPE = "dragon_breath";
    public static final MapCodec<DragonBreathHabitat> CODEC = MapCodec.unit(INSTANCE);

    static {
        Habitat.register(TYPE, CODEC);
    }

    @Override
    public int getHabitatPoints(Level level, BlockPos pos) {
        return !level.getEntities(EntityType.AREA_EFFECT_CLOUD,
                new AABB(pos),
                c -> c.getParticle() == ParticleTypes.DRAGON_BREATH).isEmpty() ? 10 : 0;
    }

    @Override
    public String typeId() {
        return TYPE;
    }
}
