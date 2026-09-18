package com.dragonspeech.effect;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Turns a sentence's spoken direction words into one combined push
 * vector. "uppa ok frama" (up and forward) sums the up vector with the
 * caster's full gaze and normalizes - the working moves things
 * exactly as spoken. Returns null when no direction words were spoken,
 * so kinetic handlers fall back to their default (away from the caster).
 */
public final class KineticDirections {

    private KineticDirections() {}

    public static Vec3 combined(EffectInvocation invocation) {
        return combined(invocation.caster(), invocation.composition().directionTags());
    }

    /**
     * Composition-free overload for callers that don't have an
     * EffectInvocation yet (CastRequestHandler building a marklaust
     * direction target before the invocation exists).
     */
    public static Vec3 combined(ServerPlayer caster, List<String> tags) {
        if (tags.isEmpty()) {
            return null;
        }

        // Direction words are relative to the caster's ACTUAL view, including pitch.
        // frama literally means "forwards; along the gaze" in the dictionary, so flattening
        // it to the horizontal plane made looking up/down have no effect.  Keep the full
        // three-dimensional look vector; up/down can still be combined with it explicitly.
        Vec3 look = caster.getLookAngle();
        if (look.lengthSqr() < 0.0001) {
            look = new Vec3(0, 0, 1);
        } else {
            look = look.normalize();
        }

        Vec3 sum = Vec3.ZERO;
        for (String tag : tags) {
            sum = switch (tag) {
                case "up" -> sum.add(0, 1, 0);
                case "down" -> sum.add(0, -1, 0);
                case "forward" -> sum.add(look);
                case "back" -> sum.subtract(look);
                default -> sum;
            };
        }

        return sum.lengthSqr() < 0.0001 ? null : sum.normalize();
    }
}
