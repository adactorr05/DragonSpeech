package com.dragonspeech.client.fx;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.phys.Vec3;

/**
 * Small steady waymarker mote - hangs in place, fading over its second half.
 * Port of EBW's ParticlePath (1.20.1 community port), 1.21.1-adapted.
 */
public class PathParticle extends DsParticle {

    public PathParticle(ClientLevel world, double x, double y, double z, SpriteSet spriteSet) {
        super(world, x, y, z, spriteSet, false);
        this.quadSize = 0.1f * 1.25f;
        this.gravity = 0;
        this.shaded = false;
        this.hasPhysics = false;
        this.setColor(1, 1, 1);
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;

        if (this.age++ >= this.lifetime) {
            this.remove();
        }

        this.move(this.xd, this.yd, this.zd);

        if (this.age > this.lifetime / 2) {
            this.setAlpha(1.0F - 2 * (((float) this.age - (float) (this.lifetime / 2)) / (float) this.lifetime));
        }
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        static SpriteSet spriteSet;

        public Provider(SpriteSet sprites) {
            spriteSet = sprites;
        }

        /** Factory used by ClientParticleSpawner via DsParticle.PROVIDERS. */
        public static DsParticle createParticle(ClientLevel level, Vec3 pos) {
            return new PathParticle(level, pos.x, pos.y, pos.z, spriteSet);
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            return new PathParticle(level, x, y, z, spriteSet);
        }
    }
}
