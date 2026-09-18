package com.dragonspeech.util;

import net.minecraft.util.Mth;

import java.util.Arrays;

/**
 * A fixed-size ring buffer of recent float values (e.g. one entry per
 * tick of the body's own yaw/pitch/height), used so the neck and tail
 * can bend by following where the body actually WAS a few ticks ago,
 * not just its current instantaneous value - this is what makes a
 * long neck/tail trail smoothly behind body motion instead of snapping
 * rigidly. Ported near-verbatim from Dragon Mounts Legacy
 * (com.github.kay9.dragonmounts.util.CircularBuffer, GPL-3.0,
 * https://github.com/TheRealKingslayer1/Dragon-Mounts-Legacy, original
 * author credit: Nico Bergemann).
 */
public class CircularBuffer {
    private final float[] buffer;
    private int index = 0;

    public CircularBuffer(int size) {
        buffer = new float[size];
    }

    public void fill(float value) {
        Arrays.fill(buffer, value);
    }

    public void update(float value) {
        index++;
        index %= buffer.length;
        buffer[index] = value;
    }

    public float get(float x, int offset) {
        int i = index - offset;
        int len = buffer.length - 1;
        return Mth.clampedLerp(buffer[i - 1 & len], buffer[i & len], x);
    }

    public float get(float x, int offset1, int offset2) {
        return get(x, offset2) - get(x, offset1);
    }
}
