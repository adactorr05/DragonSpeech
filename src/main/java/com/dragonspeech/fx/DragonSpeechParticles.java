package com.dragonspeech.fx;

import com.dragonspeech.DragonSpeech;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * Dragon Speech's magic particle types - the full set ported from
 * Electroblob's Wizardry (whose 132 particle textures now live in
 * assets/dragonspeech/textures/particle, with sprite lists in
 * assets/dragonspeech/particles). Types are registered here on the common
 * side; their renderers/factories are registered client-side in
 * DragonSpeechParticleFactories.
 *
 * VERSION-RISK NOTE: FabricParticleTypes.simple() is fabric-particles-v1's
 * standard helper for a SimpleParticleType with no extra data - stable
 * across 1.20-1.21, but if it doesn't resolve, `new SimpleParticleType(false){}`
 * is the direct equivalent.
 */
public final class DragonSpeechParticles {

    public static final SimpleParticleType BEAM = FabricParticleTypes.simple(true);
    public static final SimpleParticleType GUARDIAN_BEAM = FabricParticleTypes.simple(true);
    public static final SimpleParticleType BUFF = FabricParticleTypes.simple();
    public static final SimpleParticleType MAGIC_FIRE = FabricParticleTypes.simple();
    public static final SimpleParticleType SPARKLE = FabricParticleTypes.simple();
    public static final SimpleParticleType DARK_MAGIC = FabricParticleTypes.simple();
    public static final SimpleParticleType SNOW = FabricParticleTypes.simple();
    public static final SimpleParticleType LEAF = FabricParticleTypes.simple();
    public static final SimpleParticleType ICE = FabricParticleTypes.simple();
    public static final SimpleParticleType CLOUD = FabricParticleTypes.simple();
    public static final SimpleParticleType MAGIC_BUBBLE = FabricParticleTypes.simple();
    public static final SimpleParticleType SPARK = FabricParticleTypes.simple();
    public static final SimpleParticleType DUST = FabricParticleTypes.simple();
    public static final SimpleParticleType LIGHTNING_PULSE = FabricParticleTypes.simple();
    public static final SimpleParticleType SPHERE = FabricParticleTypes.simple(true);
    public static final SimpleParticleType SHIELD_SHELL = FabricParticleTypes.simple(true);
    public static final SimpleParticleType FLASH = FabricParticleTypes.simple();
    public static final SimpleParticleType SCORCH = FabricParticleTypes.simple();
    public static final SimpleParticleType PATH = FabricParticleTypes.simple();
    public static final SimpleParticleType LIGHTNING = FabricParticleTypes.simple(true);
    /** Purple-and-gray gravity motes - thyngja/thyngdbinda's own texture, not a reskinned vanilla particle. See fx/GravityParticle in the client package. */
    public static final SimpleParticleType GRAVITY = FabricParticleTypes.simple();

    private DragonSpeechParticles() {}

    /** Call once from onInitialize(), before anything can spawn fx. */
    public static void register() {
        register("beam", BEAM);
        register("guardian_beam", GUARDIAN_BEAM);
        register("buff", BUFF);
        register("magic_fire", MAGIC_FIRE);
        register("sparkle", SPARKLE);
        register("dark_magic", DARK_MAGIC);
        register("snow", SNOW);
        register("leaf", LEAF);
        register("ice", ICE);
        register("cloud", CLOUD);
        register("magic_bubble", MAGIC_BUBBLE);
        register("spark", SPARK);
        register("dust", DUST);
        register("lightning_pulse", LIGHTNING_PULSE);
        register("sphere", SPHERE);
        register("shield_shell", SHIELD_SHELL);
        register("flash", FLASH);
        register("scorch", SCORCH);
        register("path", PATH);
        register("lightning", LIGHTNING);
        register("gravity", GRAVITY);
    }

    private static void register(String name, SimpleParticleType type) {
        Registry.register(BuiltInRegistries.PARTICLE_TYPE, DragonSpeech.id(name), type);
    }
}
