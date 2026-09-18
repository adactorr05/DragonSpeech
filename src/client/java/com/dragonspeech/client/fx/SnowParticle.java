package com.dragonspeech.client.fx;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.phys.Vec3;

/**
 * Drifting snowflake for ice workings - falls gently, tinted slightly blue-white.
 * Port of EBW's ParticleSnow (1.20.1 community port), 1.21.1-adapted.
 */
public class SnowParticle extends DsParticle {

    public SnowParticle(ClientLevel world, double x, double y, double z, SpriteSet spriteSet) {
        super(world, x, y, z, spriteSet, true);
        this.setParticleSpeed(0, -0.02, 0);
        this.scale(0.6f);
        this.gravity = 0;
        this.hasPhysics = true;
        this.setLifetime(40 + random.nextInt(10));
        this.setColor(0.9f + 0.1f * random.nextFloat(), 0.95f + 0.05f * random.nextFloat(), 1);

        this.setSprite(spriteSet.get(world.random));
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        static SpriteSet spriteSet;

        public Provider(SpriteSet sprites) {
            spriteSet = sprites;
        }

        /** Factory used by ClientParticleSpawner via DsParticle.PROVIDERS. */
        public static DsParticle createParticle(ClientLevel level, Vec3 pos) {
            return new SnowParticle(level, pos.x, pos.y, pos.z, spriteSet);
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            return new SnowParticle(level, x, y, z, spriteSet);
        }
    }
}
