package com.dragonspeech.client.fx;

import com.dragonspeech.fx.DragonSpeechParticles;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.phys.Vec3;

import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Wires every DsParticle class to its particle type, twice over:
 *
 * 1. ParticleFactoryRegistry - so vanilla's particle engine can construct
 *    them (and load their sprite sets from assets/dragonspeech/particles).
 * 2. DsParticle.PROVIDERS - the (world, pos) factories ClientParticleSpawner
 *    uses to build fully-parameterized particles from ParticleSpawnPayload.
 *
 * Call registerAll() once from DragonSpeechClient.onInitializeClient().
 *
 * VERSION-RISK NOTE: ParticleFactoryRegistry.register(type, Provider::new)
 * is the standard fabric "sprite-aware factory" registration (the
 * Provider::new method reference satisfies PendingParticleFactory). If it
 * doesn't resolve, check fabric-particles-v1's ParticleFactoryRegistry in
 * your fabric-api version for the current pending-factory signature.
 */
public final class DragonSpeechParticleFactories {

    private DragonSpeechParticleFactories() {}

    public static void registerAll() {
        register(DragonSpeechParticles.SPARKLE, SparkleParticle.Provider::new, SparkleParticle.Provider::createParticle);
        register(DragonSpeechParticles.MAGIC_FIRE, MagicFireParticle.Provider::new, MagicFireParticle.Provider::createParticle);
        register(DragonSpeechParticles.DARK_MAGIC, DarkMagicParticle.Provider::new, DarkMagicParticle.Provider::createParticle);
        register(DragonSpeechParticles.SNOW, SnowParticle.Provider::new, SnowParticle.Provider::createParticle);
        register(DragonSpeechParticles.LEAF, LeafParticle.Provider::new, LeafParticle.Provider::createParticle);
        register(DragonSpeechParticles.ICE, IceParticle.Provider::new, IceParticle.Provider::createParticle);
        register(DragonSpeechParticles.CLOUD, CloudParticle.Provider::new, CloudParticle.Provider::createParticle);
        register(DragonSpeechParticles.MAGIC_BUBBLE, MagicBubbleParticle.Provider::new, MagicBubbleParticle.Provider::createParticle);
        register(DragonSpeechParticles.SPARK, SparkParticle.Provider::new, SparkParticle.Provider::createParticle);
        register(DragonSpeechParticles.DUST, DustParticle.Provider::new, DustParticle.Provider::createParticle);
        register(DragonSpeechParticles.LIGHTNING_PULSE, LightningPulseParticle.Provider::new, LightningPulseParticle.Provider::createParticle);
        register(DragonSpeechParticles.SCORCH, ScorchParticle.Provider::new, ScorchParticle.Provider::createParticle);
        register(DragonSpeechParticles.PATH, PathParticle.Provider::new, PathParticle.Provider::createParticle);
        register(DragonSpeechParticles.FLASH, FlashParticle.Provider::new, FlashParticle.Provider::createParticle);
        register(DragonSpeechParticles.SPHERE, SphereParticle.Provider::new, SphereParticle.Provider::createParticle);
        register(DragonSpeechParticles.SHIELD_SHELL, ShieldShellParticle.Provider::new, ShieldShellParticle.Provider::createParticle);
        register(DragonSpeechParticles.BUFF, BuffParticle.Provider::new, BuffParticle.Provider::createParticle);
        register(DragonSpeechParticles.BEAM, BeamParticle.Provider::new, BeamParticle.Provider::createParticle);
        register(DragonSpeechParticles.GUARDIAN_BEAM, GuardianBeamParticle.Provider::new, GuardianBeamParticle.Provider::createParticle);
        register(DragonSpeechParticles.LIGHTNING, LightningParticle.Provider::new, LightningParticle.Provider::createParticle);
        register(DragonSpeechParticles.GRAVITY, GravityParticle.Provider::new, GravityParticle.Provider::createParticle);
    }

    private static void register(SimpleParticleType type,
                                 Function<SpriteSet, ParticleProvider<SimpleParticleType>> vanillaFactory,
                                 BiFunction<ClientLevel, Vec3, DsParticle> spawnerFactory) {
        ParticleFactoryRegistry.getInstance().register(type, vanillaFactory::apply);
        DsParticle.PROVIDERS.put(type, spawnerFactory);
    }
}
