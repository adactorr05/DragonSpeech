package com.dragonspeech.client.fx;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.phys.Vec3;

/**
 * Heavy opaque swirl for death/shadow workings and summons - rises slowly, collides.
 * Port of EBW's ParticleDarkMagic (1.20.1 community port), 1.21.1-adapted.
 */
public class DarkMagicParticle extends DsParticle {

    public DarkMagicParticle(ClientLevel world, double x, double y, double z, SpriteSet spriteSet) {
        super(world, x, y, z, spriteSet, true);
        this.setColor(1, 1, 1);
        this.setParticleSpeed(0, 0.07000000298023224D, 0);
        this.scale(1.25F);
        this.setLifetime((int) (8.0D / (Math.random() * 0.8D + 0.2D)));
        this.hasPhysics = true;
    }

    @Override
    public net.minecraft.client.particle.ParticleRenderType getRenderType() {
        return net.minecraft.client.particle.ParticleRenderType.PARTICLE_SHEET_OPAQUE;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        static SpriteSet spriteSet;

        public Provider(SpriteSet sprites) {
            spriteSet = sprites;
        }

        /** Factory used by ClientParticleSpawner via DsParticle.PROVIDERS. */
        public static DsParticle createParticle(ClientLevel level, Vec3 pos) {
            return new DarkMagicParticle(level, pos.x, pos.y, pos.z, spriteSet);
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            return new DarkMagicParticle(level, x, y, z, spriteSet);
        }
    }
}
