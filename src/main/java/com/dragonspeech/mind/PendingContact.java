package com.dragonspeech.mind;

import java.util.UUID;

/**
 * A Contact attempt that hasn't resolved yet - the "reaching out" travel
 * time the user asked for: reaching out is no longer instant, it's a
 * line that takes real time to cross the distance to the target, faster
 * the more the attacker has mastered the skill (see durationTicks in
 * PendingContactManager), and further hasten-able by spending Stamina
 * mid-reach (see HastenContactPayload/PendingContactManager.hasten()).
 *
 * Only ever one pending contact per attacker - PendingContactManager
 * enforces that, same "no double engagement" rule as everywhere else in
 * this system.
 */
public final class PendingContact {

    private final UUID attackerId;
    private final UUID targetId;
    private final long startGameTime;
    private long durationTicks;

    public PendingContact(UUID attackerId, UUID targetId, long startGameTime, long durationTicks) {
        this.attackerId = attackerId;
        this.targetId = targetId;
        this.startGameTime = startGameTime;
        this.durationTicks = durationTicks;
    }

    public UUID attackerId() {
        return attackerId;
    }

    public UUID targetId() {
        return targetId;
    }

    public long startGameTime() {
        return startGameTime;
    }

    public long durationTicks() {
        return durationTicks;
    }

    public long deadlineGameTime() {
        return startGameTime + durationTicks;
    }

    public boolean isReady(long now) {
        return now >= deadlineGameTime();
    }

    /** Shortens the remaining travel time - never below a small floor, so hastening can't make the reach literally instant regardless of Stamina spent. */
    public void hasten(long ticksToRemove, long now) {
        long remaining = Math.max(0L, deadlineGameTime() - now);
        long minRemaining = 2L;
        long newRemaining = Math.max(minRemaining, remaining - ticksToRemove);
        this.durationTicks = (now - startGameTime) + newRemaining;
    }
}
