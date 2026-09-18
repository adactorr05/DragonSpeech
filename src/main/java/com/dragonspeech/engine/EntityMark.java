package com.dragonspeech.engine;

/**
 * "marka" + "blidr"/"illr" - a lasting tag on an entity, checked right
 * now by MagicBarrierEntity's ward/cage exclusion (GOOD passes through
 * anything, like a player; BAD is always subject to it, even a player),
 * and meant to be read by future systems too (scrying, curses/blessings -
 * not built yet, this is deliberately just the shared vocabulary/storage
 * those will eventually consume, per the request that named them).
 */
public enum EntityMark {
    GOOD,
    BAD
}
