package com.dragonspeech.client.fx;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.phys.Vec3;

/**
 * A soft pulsing flash - grows quickly, shrinks and fades with an exponential falloff.
 * Port of EBW's ParticleFlash (1.20.1 community port), 1.21.1-adapted.
 */
public class FlashParticle extends DsParticle {

    public FlashParticle(ClientLevel world, double x, double y, double z, SpriteSet spriteSet) {
        super(world, x, y, z, spriteSet, false);
        this.setColor(1, 1, 1);
        this.lifetime = 24;
    }

    @Override
    public void render(com.mojang.blaze3d.vertex.VertexConsumer buffer, net.minecraft.client.Camera camera, float partialTicks) {
        float ageProgress = ((float) this.age + partialTicks) / (float) this.lifetime;
        float exponentialFade = (float) Math.pow(1.0F - ageProgress, 3.5);
        float maxAlpha = 0.6F;
        float fadeIn = 1.0F;
        if (ageProgress < 0.2F) {
            fadeIn = net.minecraft.util.Mth.sin((ageProgress / 0.2F) * 0.5F * (float) Math.PI);
        }

        float alpha = maxAlpha * exponentialFade * fadeIn;

        this.setAlpha(Math.max(0, alpha));

        super.render(buffer, camera, partialTicks);
    }

    @Override
    public float getQuadSize(float scaleFactor) {
        float ageProgress = ((float) this.age + scaleFactor) / (float) this.lifetime;
        // EBW used a flat 0.4F base; the (quadSize / 0.1F) factor keeps that
        // exact size at default scale while still respecting scale() calls
        // (TextureSheetParticle's default quadSize is 0.1).
        float baseScale = 0.4F * (this.quadSize / 0.1F);

        float growthProgress = Math.min(ageProgress * 2.5F, 1.0F);
        float sizeMultiplier = net.minecraft.util.Mth.sin(growthProgress * 0.5F * (float) Math.PI);

        if (ageProgress > 0.3F) {
            float shrinkProgress = (ageProgress - 0.3F) / 0.7F;
            float shrinkFactor = 1.0F - (shrinkProgress * 0.6F);
            sizeMultiplier *= shrinkFactor;
        }

        return baseScale * sizeMultiplier;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        static SpriteSet spriteSet;

        public Provider(SpriteSet sprites) {
            spriteSet = sprites;
        }

        /** Factory used by ClientParticleSpawner via DsParticle.PROVIDERS. */
        public static DsParticle createParticle(ClientLevel level, Vec3 pos) {
            return new FlashParticle(level, pos.x, pos.y, pos.z, spriteSet);
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            return new FlashParticle(level, x, y, z, spriteSet);
        }
    }
}
