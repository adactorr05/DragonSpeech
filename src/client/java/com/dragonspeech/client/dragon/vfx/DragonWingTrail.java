package com.dragonspeech.client.dragon.vfx;

import net.minecraft.world.phys.Vec3;

/**
 * "Make sure the particles you use are the same as saints" per
 * explicit direction. Real finding worth being upfront about: Saints
 * Dragons doesn't actually use Minecraft's particle system for this
 * effect at all - it's a custom, hand-built ribbon-mesh renderer with
 * its own recorded position history, not discrete particle spawns.
 * Ported directly rather than substitute vanilla ParticleTypes for
 * something that would look and behave differently.
 *
 * Direct port of Saints Dragons' own DragonWingTrail (GPL - Saints
 * Dragons is itself an open-source Fabric/Forge mod) - a fixed-
 * capacity ring buffer of recent world positions plus per-point alpha,
 * unchanged from their real implementation.
 */
public final class DragonWingTrail {
    private final Vec3[] positions;
    private final float[] alpha;
    private int headIndex;
    private int pointCount;

    public DragonWingTrail(int capacity) {
        this.positions = new Vec3[capacity];
        this.alpha = new float[capacity];
    }

    public void add(Vec3 position, float alphaValue) {
        int targetIndex;
        if (pointCount < positions.length) {
            targetIndex = (headIndex + pointCount) % positions.length;
            pointCount++;
        } else {
            targetIndex = headIndex;
            headIndex = (headIndex + 1) % positions.length;
        }
        positions[targetIndex] = position;
        alpha[targetIndex] = alphaValue;
    }

    public void decay() {
        if (pointCount > 0) {
            headIndex = (headIndex + 1) % positions.length;
            pointCount--;
        }
    }

    public int getPointCount() {
        return pointCount;
    }

    public int getCapacity() {
        return positions.length;
    }

    public int getHeadIndex() {
        return headIndex;
    }

    public Vec3 getPositionAt(int logicalIndex) {
        return positions[(headIndex + logicalIndex) % positions.length];
    }

    public float getAlphaAt(int logicalIndex) {
        return alpha[(headIndex + logicalIndex) % alpha.length];
    }
}
