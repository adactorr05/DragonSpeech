package com.dragonspeech.mind;

/**
 * One live fracture on the barrier during Defense Breach. The attacker can
 * click anywhere on the barrier to create or widen a crack at that exact
 * point; the defender then clicks that visible crack to seal it.
 *
 * variantId (1-32) selects which of the pre-made crack/mend art overlays
 * to render - see MindTextures and CrackArt on the client side. Purely
 * cosmetic; gameplay never depends on which variant a crack is.
 */
public final class Crack {

    private final int id;
    private final int variantId;
    private final float x;
    private final float y;
    /** 0 = fully sealed (about to reset to a fresh, unopened crack), 100 = fully open (bursts, damaging the overall barrier, then a new crack spawns elsewhere). */
    private float openness;

    public Crack(int id, int variantId, float x, float y, float openness) {
        this.id = id;
        this.variantId = variantId;
        this.x = Math.max(0.05f, Math.min(0.95f, x));
        this.y = Math.max(0.05f, Math.min(0.95f, y));
        this.openness = openness;
    }

    public int id() {
        return id;
    }

    public int variantId() {
        return variantId;
    }

    public float x() {
        return x;
    }

    public float y() {
        return y;
    }

    public float openness() {
        return openness;
    }

    public void widen(float amount) {
        openness = Math.min(100f, openness + Math.max(0f, amount));
    }

    public void seal(float amount) {
        openness = Math.max(0f, openness - Math.max(0f, amount));
    }

    public boolean isBurstOpen() {
        return openness >= 100f;
    }

    public boolean isFullySealed() {
        return openness <= 0f;
    }
}
