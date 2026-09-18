package com.dragonspeech.client.fx;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.phys.Vec3;

/**
 * Falling leaf in randomized greens - life/growth workings.
 * Port of EBW's ParticleLeaf (1.20.1 community port), 1.21.1-adapted.
 */
public class LeafParticle extends DsParticle {

    public LeafParticle(ClientLevel world, double x, double y, double z, SpriteSet spriteSet) {
        super(world, x, y, z, spriteSet, false);
        this.setParticleSpeed(0, -0.03, 0);
        this.setLifetime(10 + random.nextInt(5));
        this.scale(1.8f);
        this.gravity = 0;
        this.hasPhysics = true;
        this.setColor(0.1f + 0.3f * random.nextFloat(), 0.5f + 0.3f * random.nextFloat(), 0.1f);

        this.setSprite(spriteSet.get(world.random));
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
            return new LeafParticle(level, pos.x, pos.y, pos.z, spriteSet);
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            return new LeafParticle(level, x, y, z, spriteSet);
        }
    }
}
