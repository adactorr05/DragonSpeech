package com.dragonspeech.client.fx;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.phys.Vec3;

/**
 * Magical flame - short-lived, physics-enabled fire licks used by every fire working.
 * Port of EBW's ParticleMagicFire (1.20.1 community port), 1.21.1-adapted.
 */
public class MagicFireParticle extends DsParticle {

    public MagicFireParticle(ClientLevel world, double x, double y, double z, SpriteSet spriteSet) {
        super(world, x, y, z, spriteSet, true);
        this.setColor(1, 1, 1);
        this.alpha = 1;
        this.lifetime = 12 + random.nextInt(4);
        this.shaded = false;
        this.hasPhysics = true;

        // Start on a random sprite of the 32-frame flame sheet.
        this.setSprite(spriteSet.get(world.random));
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        static SpriteSet spriteSet;

        public Provider(SpriteSet sprites) {
            spriteSet = sprites;
        }

        /** Factory used by ClientParticleSpawner via DsParticle.PROVIDERS. */
        public static DsParticle createParticle(ClientLevel level, Vec3 pos) {
            return new MagicFireParticle(level, pos.x, pos.y, pos.z, spriteSet);
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            return new MagicFireParticle(level, x, y, z, spriteSet);
        }
    }
}
