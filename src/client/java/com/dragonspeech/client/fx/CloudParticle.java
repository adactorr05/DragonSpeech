package com.dragonspeech.client.fx;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.phys.Vec3;

/**
 * Large soft cloud puff that fades in and out - storm clouds and mists.
 * Port of EBW's ParticleCloud (1.20.1 community port), 1.21.1-adapted.
 */
public class CloudParticle extends DsParticle {

    public CloudParticle(ClientLevel world, double x, double y, double z, SpriteSet spriteSet) {
        super(world, x, y, z, spriteSet, false);
        this.setColor(1, 1, 1);
        this.lifetime = 48 + this.random.nextInt(12);
        this.scale(3);
        this.setGravity(false);
        this.setAlpha(0);
        this.hasPhysics = false;
        this.shaded = false;
    }

    @Override
    public net.minecraft.client.particle.ParticleRenderType getRenderType() {
        return net.minecraft.client.particle.ParticleRenderType.PARTICLE_SHEET_LIT;
    }

    @Override
    public void tick() {
        super.tick();

        float fadeTime = this.lifetime * 0.3f;
        this.setAlpha(net.minecraft.util.Mth.clamp(Math.min(this.age / fadeTime, (this.lifetime - this.age) / fadeTime), 0, 1));
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        static SpriteSet spriteSet;

        public Provider(SpriteSet sprites) {
            spriteSet = sprites;
        }

        /** Factory used by ClientParticleSpawner via DsParticle.PROVIDERS. */
        public static DsParticle createParticle(ClientLevel level, Vec3 pos) {
            return new CloudParticle(level, pos.x, pos.y, pos.z, spriteSet);
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            return new CloudParticle(level, x, y, z, spriteSet);
        }
    }
}
