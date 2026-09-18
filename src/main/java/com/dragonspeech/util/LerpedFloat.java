package com.dragonspeech.util;

import net.minecraft.util.Mth;

/**
 * A float that remembers its previous value, so a render tick can ask
 * for a smoothly interpolated value at any partial-tick fraction
 * between two real, once-per-server-tick updates - instead of a raw
 * value snapping visibly between ticks. Ported near-verbatim from
 * Dragon Mounts Legacy (com.github.kay9.dragonmounts.util.LerpedFloat,
 * GPL-3.0, https://github.com/TheRealKingslayer1/Dragon-Mounts-Legacy)
 * - this is small, self-contained utility code with no meaningful
 * design decision to make differently, so a faithful, direct port
 * made more sense than reinventing the same thing.
 */
public class LerpedFloat {
    protected float current;
    protected float previous;

    public LerpedFloat() {
        current = previous = 0;
    }

    public LerpedFloat(float start) {
        current = previous = start;
    }

    public float get(float x) {
        return Mth.clampedLerp(previous, current, x);
    }

    public float get() {
        return current;
    }

    public void set(float value) {
        sync();
        current = value;
    }

    public void add(float value) {
        sync();
        current += value;
    }

    public void sync() {
        previous = current;
    }

    public float getPrevious() {
        return previous;
    }

    public static LerpedFloat.Clamped unit() {
        return new Clamped(0, 1);
    }

    /** Keeps the value clamped within min/max as it's set/added to. */
    public static class Clamped extends LerpedFloat {
        private final float min;
        private final float max;

        public Clamped(float start, float min, float max) {
            super(Mth.clamp(start, min, max));
            this.min = min;
            this.max = max;
        }

        public Clamped(float min, float max) {
            this(0, min, max);
        }

        @Override
        public void set(float value) {
            super.set(Mth.clamp(value, min, max));
        }

        @Override
        public void add(float value) {
            super.add(value);
            current = Mth.clamp(current, min, max);
        }

        public float getMin() {
            return min;
        }

        public float getMax() {
            return max;
        }
    }
}
