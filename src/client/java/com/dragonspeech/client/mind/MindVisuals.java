package com.dragonspeech.client.mind;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Small procedural drawing toolkit shared by MindDuelScreen and
 * TeamMindDuelScreen. There are no custom texture/PNG assets for this
 * system - GuiGraphics.fill() only draws axis-aligned rectangles - so
 * "bespoke art" here means building circles, rings, and lines out of
 * scanline rectangle fills instead of relying on artwork that doesn't
 * exist. This gets meaningfully closer to the original mockups' feel
 * (a barrier with radiating cracks, a tug-of-war beam, scattered round
 * nodes) without needing an actual texture pipeline.
 */
public final class MindVisuals {

    private MindVisuals() {}

    /** A filled circle, built one horizontal scanline at a time. Cheap enough for the radii this system uses (typically under ~60px). */
    public static void fillCircle(GuiGraphics graphics, int cx, int cy, int radius, int color) {
        for (int dy = -radius; dy <= radius; dy++) {
            int dx = (int) Math.round(Math.sqrt(Math.max(0, radius * radius - dy * dy)));
            if (dx > 0) {
                graphics.fill(cx - dx, cy + dy, cx + dx, cy + dy + 1, color);
            }
        }
    }

    /** An unfilled circle outline, `thickness` pixels wide - a ring drawn as (outer circle) minus (inner circle). */
    public static void drawRing(GuiGraphics graphics, int cx, int cy, int outerRadius, int thickness, int color) {
        int innerRadius = Math.max(0, outerRadius - thickness);
        for (int dy = -outerRadius; dy <= outerRadius; dy++) {
            int outerDx = (int) Math.round(Math.sqrt(Math.max(0, outerRadius * outerRadius - dy * dy)));
            if (outerDx <= 0) {
                continue;
            }
            if (Math.abs(dy) >= innerRadius) {
                // Fully outside the inner circle at this row - the whole strip is ring.
                graphics.fill(cx - outerDx, cy + dy, cx + outerDx, cy + dy + 1, color);
            } else {
                int innerDx = (int) Math.round(Math.sqrt(Math.max(0, innerRadius * innerRadius - dy * dy)));
                graphics.fill(cx - outerDx, cy + dy, cx - innerDx, cy + dy + 1, color);
                graphics.fill(cx + innerDx, cy + dy, cx + outerDx, cy + dy + 1, color);
            }
        }
    }

    /** A straight line of the given pixel thickness, stepped point-by-point (a simple DDA), used for procedural cracks. */
    public static void drawLine(GuiGraphics graphics, double x0, double y0, double x1, double y1, int thickness, int color) {
        double dx = x1 - x0, dy = y1 - y0;
        int steps = Math.max(1, (int) Math.ceil(Math.max(Math.abs(dx), Math.abs(dy))));
        double stepX = dx / steps, stepY = dy / steps;
        double x = x0, y = y0;
        int half = Math.max(1, thickness / 2);
        for (int i = 0; i <= steps; i++) {
            graphics.fill((int) x - half, (int) y - half, (int) x + half + 1, (int) y + half + 1, color);
            x += stepX;
            y += stepY;
        }
    }

    /** Deterministic "random" in [0,1) from an integer seed - used so a given node/crack always renders at the same angle/offset instead of jittering every frame. */
    public static float stableRandom(int seed) {
        int h = seed * 0x9E3779B1;
        h ^= (h >>> 15);
        return (h & 0x7FFFFFFF) / (float) Integer.MAX_VALUE;
    }

    /** 0..1 breathing pulse, period in milliseconds - used for the true core reveal, low-Focus warnings elsewhere, etc. */
    public static float pulse(long periodMillis) {
        return (float) (0.5 + 0.5 * Math.sin(System.currentTimeMillis() * (2 * Math.PI / periodMillis)));
    }
}
