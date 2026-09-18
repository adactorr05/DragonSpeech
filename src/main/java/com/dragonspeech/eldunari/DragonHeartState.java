package com.dragonspeech.eldunari;

/**
 * Renamed from EldunariState - see DragonSpeechHearts/DragonHeartItem
 * for the rename's own reasoning (player-facing "Eldunari" terminology
 * was a copyright concern; this pass extends the rename to the internal
 * class names too, for the maintainer's own consistency, not a new
 * player-facing concern).
 *
 * "If you have gained permission from the heart (or broken its mind)
 * you can use that stamina as your own." Two completely different paths
 * to the same USABLE end-state:
 *
 *   PERMITTED - the dragon it came from gave it freely (see
 *   EldunariService#giveOwnHeart, triggered by the "Give Heart" button
 *   on DragonBondScreen - only obtainable from YOUR OWN bonded dragon).
 *   No combat, no risk, but only your own dragon(s) can ever grant this.
 *
 *   BROKEN - the player won a mind duel against the heart's own
 *   residual consciousness (a DRAGON-tier fight - "dragons are the
 *   strongest," full defensive AI via the existing MobMindCombatAI, same
 *   as fighting the living dragon it came from). Works on ANY heart,
 *   including ones with no known living dragon, but is a genuine DRAGON-
 *   tier mind duel - not a trivial thing to win. Mad hearts (see
 *   DragonHeartItem.isMad()) are harder still.
 */
public enum DragonHeartState {
    /** Dormant - a residual mind is still present but the player hasn't gained access either way yet. Right-click to attempt contact. */
    UNBONDED,
    /** Freely given by a bonded dragon. Usable immediately, no further check. */
    PERMITTED,
    /** Access forced open by winning a mind duel against it. Usable immediately, same as PERMITTED from here on. */
    BROKEN,
    /** A mind duel against this heart is currently in progress - prevents starting a second, overlapping attempt on the same item. */
    CONTESTED;

    public boolean usable() {
        return this == PERMITTED || this == BROKEN;
    }
}
