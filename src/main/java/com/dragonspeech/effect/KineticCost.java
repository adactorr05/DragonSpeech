package com.dragonspeech.effect;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * The physics of the old tongue: moving matter costs what physics says
 * it costs. Every kinetic handler prices its work through these three
 * factors, exactly per the source material's rule that magic obeys the
 * same energy arithmetic as muscle:
 *
 *  MASS       - a cow is more work than a chicken. Bounding-box volume
 *               is the mass proxy (no real mass exists in the engine).
 *
 *  OPPOSITION - fighting a body's existing motion costs extra in
 *               proportion to how fast it's moving against you.
 *               Arresting a long fall at terminal speed is drastically
 *               more work than nudging something at rest - the faster
 *               the fall, the more it takes to stop it.
 *
 *  ALTITUDE   - work done against gravity accumulates: the higher the
 *               target already is, the more each further push upward
 *               costs. Riding "lyfta sjalfan" ever upward gets more
 *               expensive with every cast, not less.
 *
 * All constants are balance knobs.
 */
public final class KineticCost {

    /** A player-sized body (~0.65 blocks^3) comes out at roughly 1.0. */
    private static final float MASS_NORMALIZER = 0.65f;

    /** Cost multiplier per block/tick of opposed velocity. Falling fast is ~1.5-3.0 blocks/tick. */
    private static final float OPPOSITION_PER_SPEED = 2.5f;

    /** Altitude surcharge: +100% cost per this many blocks above the baseline. */
    private static final float ALTITUDE_DOUBLING_BLOCKS = 48f;

    /** Below this height, upward work carries no surcharge. */
    private static final float ALTITUDE_BASELINE_Y = 64f;

    private KineticCost() {}

    /** Bounding-box volume relative to a player. Clamped so silverfish aren't free and ghasts aren't bankrupting. */
    public static float massFactor(Entity entity) {
        float volume = entity.getBbWidth() * entity.getBbWidth() * entity.getBbHeight();
        return Math.max(0.3f, Math.min(volume / MASS_NORMALIZER, 6.0f));
    }

    /**
     * How hard this body's current motion fights the intended direction.
     * 1.0 = no opposition (at rest, or already moving that way).
     * Scales up with the speed component moving AGAINST the intent -
     * this is what makes catching a fall expensive in proportion to how
     * far gone it already is.
     */
    public static float oppositionFactor(Entity entity, Vec3 intendedDirection) {
        Vec3 motion = entity.getDeltaMovement();
        if (motion.lengthSqr() < 0.0001 || intendedDirection.lengthSqr() < 0.0001) {
            return 1.0f;
        }
        double against = -motion.dot(intendedDirection.normalize());
        if (against <= 0) {
            return 1.0f; // moving with the push, or perpendicular - no fight
        }
        return 1.0f + (float) against * OPPOSITION_PER_SPEED;
    }

    /** Surcharge for upward work at height - each ~48 blocks above y=64 doubles the cost of pushing higher still. */
    public static float altitudeFactor(Entity entity, Vec3 intendedDirection) {
        if (intendedDirection.y <= 0.01) {
            return 1.0f; // only upward work fights accumulated gravity
        }
        float above = (float) Math.max(0, entity.getY() - ALTITUDE_BASELINE_Y);
        return 1.0f + (above / ALTITUDE_DOUBLING_BLOCKS) * (float) intendedDirection.y;
    }

    /** All three combined for one target and one intent. */
    public static float weightFor(Entity entity, Vec3 intendedDirection) {
        return massFactor(entity)
            * oppositionFactor(entity, intendedDirection)
            * altitudeFactor(entity, intendedDirection);
    }
}
