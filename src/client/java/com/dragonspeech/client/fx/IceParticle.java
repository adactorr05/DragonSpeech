package com.dragonspeech.client.fx;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.phys.Vec3;

/**
 * Ice shard fragment - gravity-affected debris for frost impacts.
 * Port of EBW's ParticleIce (1.20.1 community port), 1.21.1-adapted.
 */
public class IceParticle extends DsParticle {

    public IceParticle(ClientLevel world, double x, double y, double z, SpriteSet spriteSet) {
        super(world, x, y, z, spriteSet, false);
        this.hasPhysics = true;

        this.setColor(1, 1, 1);
        this.scale(1.2f);
        this.setGravity(true);
        this.shaded = false;

        this.setSprite(spriteSet.get(world.random));
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        static SpriteSet spriteSet;

        public Provider(SpriteSet sprites) {
            spriteSet = sprites;
        }

        /** Factory used by ClientParticleSpawner via DsParticle.PROVIDERS. */
        public static DsParticle createParticle(ClientLevel level, Vec3 pos) {
            return new IceParticle(level, pos.x, pos.y, pos.z, spriteSet);
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            return new IceParticle(level, x, y, z, spriteSet);
        }
    }
}
