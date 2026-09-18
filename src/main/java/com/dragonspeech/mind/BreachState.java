package com.dragonspeech.mind;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Live state for Defense Breach's real-time click loop. Cracks are created
 * where the attacker clicks, not at pre-authored weak points. The defender
 * can mend only visible cracks by clicking them.
 *
 * Per-side click cooldowns live here too - this is what turns clicking
 * into an actual reaction game instead of a macro contest. Speed
 * shortens the cooldown (see MindCombatant.speed() and
 * MindDuelActionService's cooldown formula).
 */
public final class BreachState {

    private static final int VARIANT_COUNT = 32;
    private static final float BURST_DAMAGE_TO_BARRIER = 8f;
    private static final float MERGE_RADIUS = 0.075f;

    private final List<Crack> cracks = new ArrayList<>();
    private int nextCrackId = 0;
    private final Random random = new Random();

    private long attackerNextClickTime = 0L;
    private long defenderNextClickTime = 0L;

    public BreachState() {
    }

    private Crack spawnCrack(float x, float y) {
        int variantId = 1 + random.nextInt(VARIANT_COUNT);
        Crack crack = new Crack(nextCrackId++, variantId, clamp01(x), clamp01(y), 0f);
        cracks.add(crack);
        return crack;
    }

    public List<Crack> cracks() {
        return cracks;
    }

    public Crack findById(int id) {
        for (Crack crack : cracks) {
            if (crack.id() == id) {
                return crack;
            }
        }
        return null;
    }

    public long attackerNextClickTime() {
        return attackerNextClickTime;
    }

    public void setAttackerNextClickTime(long time) {
        this.attackerNextClickTime = time;
    }

    public long defenderNextClickTime() {
        return defenderNextClickTime;
    }

    public void setDefenderNextClickTime(long time) {
        this.defenderNextClickTime = time;
    }

    /** Widens the nearest crack to the clicked point, or creates a new one there. */
    public float strikeAt(float x, float y, float amount) {
        Crack crack = findNearest(x, y, MERGE_RADIUS);
        if (crack == null) {
            crack = spawnCrack(x, y);
        }
        crack.widen(amount);
        if (crack.isBurstOpen()) {
            cracks.remove(crack);
            return BURST_DAMAGE_TO_BARRIER;
        }
        return 0f;
    }

    /** Seals a crack. Fully sealed cracks disappear from the defender's click targets. */
    public void seal(int crackId, float amount) {
        Crack crack = findById(crackId);
        if (crack != null) {
            crack.seal(amount);
            if (crack.isFullySealed()) {
                cracks.remove(crack);
            }
        }
    }

    private Crack findNearest(float x, float y, float radius) {
        x = clamp01(x);
        y = clamp01(y);
        Crack best = null;
        float bestDist = radius * radius;
        for (Crack crack : cracks) {
            float dx = crack.x() - x;
            float dy = crack.y() - y;
            float dist = dx * dx + dy * dy;
            if (dist <= bestDist) {
                best = crack;
                bestDist = dist;
            }
        }
        return best;
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
