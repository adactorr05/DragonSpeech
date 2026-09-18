package com.dragonspeech.client.mind;

/**
 * Client-only tracking for the currently-rendering Contact beam (if
 * any). The timer here is purely a local visual approximation - the
 * server's own PendingContact is the actual authority on when Contact
 * resolves; this just needs to look right, not be perfectly in sync.
 */
public final class ContactBeamState {

    private static Integer targetEntityId = null;
    private static long startClientTick = 0L;
    private static long durationTicks = 0L;
    private static long clientTickCounter = 0L;
    private static long pathSeed = 0L;

    private ContactBeamState() {}

    public static void onClientTick() {
        clientTickCounter++;
    }

    public static void start(int targetEntityId, long durationTicks) {
        ContactBeamState.targetEntityId = targetEntityId;
        ContactBeamState.startClientTick = clientTickCounter;
        ContactBeamState.durationTicks = Math.max(1L, durationTicks);
        ContactBeamState.pathSeed = clientTickCounter; // fixed once per reach - keeps the path's wind pattern stable across frames
    }

    /**
     * Updates the total duration (measured from the ORIGINAL start tick,
     * not from now) without touching startClientTick or pathSeed - used
     * when hastening shortens the server's remaining time mid-reach.
     * Calling start() again instead would reset progress to 0, causing
     * the beam to visibly snap back to the beginning every time hasten
     * fires (which is often, while the key is held) rather than simply
     * speeding up smoothly toward the target.
     */
    public static void adjustDuration(int targetEntityId, long newTotalDurationTicks) {
        if (!isActive() || !targetEntityId().equals(targetEntityId)) {
            start(targetEntityId, newTotalDurationTicks);
            return;
        }
        ContactBeamState.durationTicks = Math.max(1L, newTotalDurationTicks);
    }

    public static long pathSeed() {
        return pathSeed;
    }

    public static void cancel() {
        targetEntityId = null;
    }

    public static boolean isActive() {
        return targetEntityId != null;
    }

    public static Integer targetEntityId() {
        return targetEntityId;
    }

    /** 0 (just started) to 1 (should have arrived) - render code should stop/cancel visually past 1 even if the server hasn't confirmed cancellation yet, so a lost packet doesn't leave a beam frozen forever. */
    public static float progress() {
        if (targetEntityId == null) {
            return 0f;
        }
        long elapsed = clientTickCounter - startClientTick;
        return Math.max(0f, Math.min(1f, elapsed / (float) durationTicks));
    }
}
