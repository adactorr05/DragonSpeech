package com.dragonspeech.client.fx;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.phys.Vec3;

/**
 * The flat expanding shockwave ring that lies on the ground under a lightning strike.
 * Port of EBW's ParticleLightningPulse (1.20.1 community port), 1.21.1-adapted.
 */
public class LightningPulseParticle extends DsParticle {

    public LightningPulseParticle(ClientLevel world, double x, double y, double z, SpriteSet spriteSet) {
        super(world, x, y, z, spriteSet, true);
        this.quadSize = 6f;
        this.setColor(1, 1, 1);
        this.shaded = false;
        this.hasPhysics = false;
        this.setLifetime(7);
        // Lies flat on the ground, facing straight up.
        this.pitch = 90;
        this.yaw = 0;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        static SpriteSet spriteSet;

        public Provider(SpriteSet sprites) {
            spriteSet = sprites;
        }

        /** Factory used by ClientParticleSpawner via DsParticle.PROVIDERS. */
        public static DsParticle createParticle(ClientLevel level, Vec3 pos) {
            return new LightningPulseParticle(level, pos.x, pos.y, pos.z, spriteSet);
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            return new LightningPulseParticle(level, x, y, z, spriteSet);
        }
    }
}
