package com.dragonspeech.client.fx;

import com.dragonspeech.DragonSpeech;
import com.dragonspeech.network.ParticleSpawnPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.function.BiFunction;

/**
 * The client end of SpellFx: turns a ParticleSpawnPayload back into fully
 * parameterized DsParticles and hands them to the particle engine. Port
 * of EBW's ParticleSpawner with one addition - the count/jitter batch, so
 * a 40-mote burst is one packet, not forty.
 *
 * Field conventions mirror EBW exactly: NaN velocity/facing/target and
 * negative colour components / lifetime / ids mean "not set - keep the
 * particle class's own default."
 */
public final class ClientParticleSpawner {

    private ClientParticleSpawner() {}

    public static void spawn(ParticleSpawnPayload data) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }

        ResourceLocation typeId = ResourceLocation.tryParse(data.particleId());
        if (typeId == null || !(BuiltInRegistries.PARTICLE_TYPE.get(typeId) instanceof SimpleParticleType type)) {
            DragonSpeech.LOGGER.warn("[DragonSpeech] Unknown particle type in spawn payload: {}", data.particleId());
            return;
        }

        BiFunction<ClientLevel, Vec3, DsParticle> factory = DsParticle.PROVIDERS.get(type);
        if (factory == null) {
            DragonSpeech.LOGGER.warn("[DragonSpeech] No client factory registered for particle: {}", data.particleId());
            return;
        }

        var random = level.random;
        int count = Math.max(1, data.count());

        // Config GUI (Client tab) "Particle Density" - a probabilistic keep-chance rather than a hard
        // truncation of `count`, so a low multiplier thins out even small (count=1) spawns instead of
        // only affecting big bursts, and a multiplier above 1.0 can occasionally double up a spawn.
        float density = com.dragonspeech.client.config.DragonSpeechClientConfig.particleDensity();

        for (int i = 0; i < count; i++) {
            if (density < 1.0f && random.nextFloat() >= density) {
                continue;
            }
            int extraSpawns = density > 1.0f && random.nextFloat() < (density - 1.0f) ? 1 : 0;
            for (int spawnPass = 0; spawnPass <= extraSpawns; spawnPass++) {
            double ox = 0, oy = 0, oz = 0;
            if (i > 0 && data.jitter() > 0) {
                ox = (random.nextDouble() * 2 - 1) * data.jitter();
                oy = (random.nextDouble() * 2 - 1) * data.jitter();
                oz = (random.nextDouble() * 2 - 1) * data.jitter();
            }

            DsParticle particle = factory.apply(level, new Vec3(data.x() + ox, data.y() + oy, data.z() + oz));

            if (!Double.isNaN(data.vx() + data.vy() + data.vz())) {
                particle.setParticleSpeed(data.vx(), data.vy(), data.vz());
            }
            if (data.r() >= 0) {
                particle.setColor(data.r(), data.g(), data.b());
            }
            if (data.fr() >= 0) {
                particle.setFadeColour(data.fr(), data.fg(), data.fb());
            }
            if (data.lifetime() >= 0) {
                particle.setLifetime(data.lifetime());
            }
            if (data.spinRadius() > 0) {
                particle.setSpin(data.spinRadius(), data.spinSpeed());
            }
            if (!Float.isNaN(data.yaw() + data.pitch())) {
                particle.setFacing(data.yaw(), data.pitch());
            }

            particle.scale(data.scale());
            particle.setGravity(data.gravity());
            particle.setShaded(data.shaded());
            particle.setCollisions(data.collide());
            if (data.seed() != 0) {
                particle.setSeed(data.seed());
            }

            if (data.entityId() >= 0) {
                particle.setEntity(level.getEntity(data.entityId()));
            }

            if (particle instanceof DsParticleTargeted targeted) {
                if (data.targetId() >= 0) {
                    targeted.setTargetEntity(level.getEntity(data.targetId()));
                }
                if (data.length() > 0) {
                    targeted.setLength(data.length());
                }
                targeted.setTargetPosition(data.tx(), data.ty(), data.tz());
            }

            Minecraft.getInstance().particleEngine.add(particle);
            }
        }
    }
}
