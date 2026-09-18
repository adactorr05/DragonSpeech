package com.dragonspeech.client.fx;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.phys.Vec3;

/**
 * The black scorch mark left on a surface - fades over its (long) lifetime and vanishes if its backing block is removed.
 * Port of EBW's ParticleScorch (1.20.1 community port), 1.21.1-adapted.
 */
public class ScorchParticle extends DsParticle {

    public ScorchParticle(ClientLevel world, double x, double y, double z, SpriteSet spriteSet) {
        super(world, x, y, z, spriteSet, false);
        this.gravity = 0;
        this.setLifetime(100 + random.nextInt(40));
        this.scale(2);
        this.setColor(0, 0, 0);
        this.shaded = false;
        this.adjustQuadSize = false;

        this.setSprite(spriteSet.get(world.random));
    }

    @Override
    public void tick() {
        super.tick();

        // Colour reaches its fade target by half-life (not full life)...
        float ageFraction = Math.min((float) this.age / ((float) this.lifetime * 0.5f), 1);

        this.rCol = this.initialRed + (this.fadeRed - this.initialRed) * ageFraction;
        this.gCol = this.initialGreen + (this.fadeGreen - this.initialGreen) * ageFraction;
        this.bCol = this.initialBlue + (this.fadeBlue - this.initialBlue) * ageFraction;

        // ...then the mark itself fades away over the second half.
        if (this.age > this.lifetime / 2) {
            this.setAlpha(1 - ((float) this.age - this.lifetime / 2f) / (this.lifetime / 2f));
        }

        // A scorch with no surface behind it is meaningless - remove it.
        net.minecraft.core.Direction facing = net.minecraft.core.Direction.fromYRot(yaw);
        if (pitch == 90) facing = net.minecraft.core.Direction.UP;
        if (pitch == -90) facing = net.minecraft.core.Direction.DOWN;

        if (!level.getBlockState(net.minecraft.core.BlockPos.containing(x, y, z).relative(facing.getOpposite())).isSolid()) {
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
            return new ScorchParticle(level, pos.x, pos.y, pos.z, spriteSet);
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            return new ScorchParticle(level, x, y, z, spriteSet);
        }
    }
}
