package com.dragonspeech.client.mind;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Builds the winding, mostly-ground-hugging path the Contact beam
 * follows - per the reference screenshot, this is NOT a straight line
 * through the air: it runs low (roughly at floor height between the two
 * points) with a gentle organic side-to-side wind, then rises in a
 * short final stretch up the target's body to about chest height,
 * matching where the thread visibly "connects."
 *
 * There's no per-point terrain raycasting here (that would mean a
 * world query for every one of ~20 points, every time the path
 * regenerates) - the ground-hugging look instead comes from simply
 * interpolating between the two feet positions (which are already at
 * ground level) rather than from eye height, plus the sideways wind for
 * visual interest. On uneven terrain this will occasionally clip into
 * small bumps rather than perfectly hug them - a reasonable trade for
 * not doing per-frame world queries along a 20+ point path.
 *
 * The seed is fixed per beam (derived from its start tick) so the path
 * looks the same every frame instead of jittering.
 */
public final class BeamPath {

    private static final int GROUND_SEGMENTS = 50;
    private static final int RISE_SEGMENTS = 10;

    private BeamPath() {}

    public static List<Vec3> build(Vec3 fromFeet, Vec3 toFeet, double toRiseHeight, long seed) {
        List<Vec3> points = new ArrayList<>();
        Random random = new Random(seed);

        double dx = toFeet.x - fromFeet.x;
        double dz = toFeet.z - fromFeet.z;
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);

        // Fixed segment count regardless of distance meant that at short
        // range, consecutive points could collapse to near-identical
        // positions - every segment reads as degenerate (near-zero
        // length) and gets skipped by the renderer, so the WHOLE ground
        // portion of the beam would render as nothing at all. Scaling
        // segment count down for short distances keeps each one a
        // meaningful, drawable length no matter how close the target is.
        int groundSegments = Math.max(2, Math.min(GROUND_SEGMENTS, (int) Math.round(horizontalDist * 3)));

        // Perpendicular (in the XZ plane) to the direct path, for the side-to-side wind.
        double perpX = -dz / Math.max(0.0001, horizontalDist);
        double perpZ = dx / Math.max(0.0001, horizontalDist);
        double windAmplitude = Math.min(0.6, horizontalDist * 0.06);
        double windPhase = random.nextDouble() * Math.PI * 2;
        int windCycles = 1 + random.nextInt(2); // one or two gentle bends along the path, not a straight line

        for (int i = 0; i <= groundSegments; i++) {
            double t = i / (double) groundSegments;
            double x = fromFeet.x + dx * t;
            double z = fromFeet.z + dz * t;
            double y = fromFeet.y + (toFeet.y - fromFeet.y) * t;

            double wind = Math.sin(t * Math.PI * windCycles + windPhase) * windAmplitude * Math.sin(t * Math.PI); // tapers to 0 at both ends
            x += perpX * wind;
            z += perpZ * wind;

            points.add(new Vec3(x, y + 0.05, z)); // small lift so it doesn't z-fight with the floor
        }

        for (int i = 1; i <= RISE_SEGMENTS; i++) {
            double t = i / (double) RISE_SEGMENTS;
            points.add(new Vec3(toFeet.x, toFeet.y + 0.05 + toRiseHeight * t, toFeet.z));
        }

        return points;
    }

    /** Truncates the path to the given progress fraction (0-1), interpolating the final partial segment so growth reads smoothly rather than jumping point-to-point. */
    public static List<Vec3> truncate(List<Vec3> fullPath, float progress) {
        if (fullPath.size() < 2) {
            return fullPath;
        }
        float clamped = Math.max(0f, Math.min(1f, progress));
        float exactIndex = clamped * (fullPath.size() - 1);
        int wholeIndex = (int) Math.floor(exactIndex);
        float frac = exactIndex - wholeIndex;

        List<Vec3> visible = new ArrayList<>(fullPath.subList(0, Math.min(fullPath.size(), wholeIndex + 1)));
        if (wholeIndex + 1 < fullPath.size() && frac > 0f) {
            Vec3 a = fullPath.get(wholeIndex);
            Vec3 b = fullPath.get(wholeIndex + 1);
            visible.add(a.add(b.subtract(a).scale(frac)));
        }
        return visible;
    }
}