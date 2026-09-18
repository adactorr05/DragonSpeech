package com.dragonspeech.client.fx;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.phys.Vec3;

/**
 * The workhorse magic mote: soft glowing sparkle that fades out over its second half.
 * Port of EBW's ParticleSparkle (1.20.1 community port), 1.21.1-adapted.
 */
public class SparkleParticle extends DsParticle {

    public SparkleParticle(ClientLevel world, double x, double y, double z, SpriteSet spriteSet) {
        super(world, x, y, z, spriteSet, true);
        this.setColor(1, 1, 1);
        this.lifetime = 48 + this.random.nextInt(12);
        this.scale(0.75f);
        this.gravity = 0;
        this.hasPhysics = false;
        this.shaded = false;
    }

    @Override
    public void tick() {
        super.tick();

        if (this.age > this.lifetime / 2) {
            this.setAlpha(1 - ((float) this.age - (float) (this.lifetime / 2)) / (float) this.lifetime);
        }
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        static SpriteSet spriteSet;

        public Provider(SpriteSet sprites) {
            spriteSet = sprites;
        }

        /** Factory used by ClientParticleSpawner via DsParticle.PROVIDERS. */
        public static DsParticle createParticle(ClientLevel level, Vec3 pos) {
            return new SparkleParticle(level, pos.x, pos.y, pos.z, spriteSet);
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            return new SparkleParticle(level, x, y, z, spriteSet);
        }
    }
}
