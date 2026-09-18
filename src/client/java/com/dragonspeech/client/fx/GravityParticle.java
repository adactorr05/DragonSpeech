package com.dragonspeech.client.fx;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.phys.Vec3;

/**
 * A small purple-gray mote for gravity workings (thyngja/thyngdbinda) and
 * gravity-augmented pushes (thrystbinda med thyngdarafl) - a real custom
 * texture (textures/particle/gravity_particle.png), not a re-tinted
 * vanilla particle. Simple single-sprite mote, same structural pattern as
 * DustParticle: SpellFx's caller controls color/fade/velocity/lifetime
 * per-spawn, this class just supplies sane defaults and how it fades.
 */
public class GravityParticle extends DsParticle {

    public GravityParticle(ClientLevel world, double x, double y, double z, SpriteSet spriteSet) {
        super(world, x, y, z, spriteSet, false);
        this.setSize(0.02F, 0.02F);
        this.quadSize *= 1.4F + this.random.nextFloat() * 0.6F;
        this.lifetime = 24 + this.random.nextInt(12);
        this.gravity = 0;
        this.shaded = false;
        // Purple core fading toward a cooler gray, matching the baked
        // texture's own gradient - SpellFx callers can still override
        // both via .color()/.fade() if a specific spell wants to.
        this.setColor(0.78f, 0.55f, 1.0f);
        this.setFadeColour(0.45f, 0.42f, 0.55f);
        this.setSprite(spriteSet.get(world.random));
    }

    @Override
    public void tick() {
        super.tick();
        // Shrinks out rather than just cutting off, so a cluster doesn't
        // visibly pop away all at once at the end of its life.
        float ageFraction = (float) this.age / (float) this.lifetime;
        this.setAlpha(1.0f - ageFraction);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        static SpriteSet spriteSet;

        public Provider(SpriteSet sprites) {
            spriteSet = sprites;
        }

        /** Factory used by ClientParticleSpawner via DsParticle.PROVIDERS. */
        public static DsParticle createParticle(ClientLevel level, Vec3 pos) {
            return new GravityParticle(level, pos.x, pos.y, pos.z, spriteSet);
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            return new GravityParticle(level, x, y, z, spriteSet);
        }
    }
}
