package com.dragonspeech.client.fx;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.phys.Vec3;

/**
 * Buoyant magical bubble - drifts upward with heavy drag; water/orb workings.
 * Port of EBW's ParticleMagicBubble (1.20.1 community port), 1.21.1-adapted.
 */
public class MagicBubbleParticle extends DsParticle {

    public MagicBubbleParticle(ClientLevel world, double x, double y, double z, SpriteSet spriteSet) {
        super(world, x, y, z, spriteSet, false);
        this.setColor(1, 1, 1);
        this.setSize(0.02F, 0.02F);
        this.scale(this.random.nextFloat() * 0.6F + 0.2F);
        this.lifetime = (int) (8.0D / (Math.random() * 0.8D + 0.2D));
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;

        this.yo += 0.002D;
        this.move(this.xd, this.yd, this.zd);
        this.xd *= 0.8500000238418579D;
        this.yd *= 0.8500000238418579D;
        this.zd *= 0.8500000238418579D;

        if (this.lifetime-- <= 0) {
            this.remove();
        }
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        static SpriteSet spriteSet;

        public Provider(SpriteSet sprites) {
            spriteSet = sprites;
        }

        /** Factory used by ClientParticleSpawner via DsParticle.PROVIDERS. */
        public static DsParticle createParticle(ClientLevel level, Vec3 pos) {
            return new MagicBubbleParticle(level, pos.x, pos.y, pos.z, spriteSet);
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            return new MagicBubbleParticle(level, x, y, z, spriteSet);
        }
    }
}
