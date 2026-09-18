package com.dragonspeech.dragon;

/**
 * FIX (my own mistake from last round, not an old pre-existing bug):
 * "cannot find symbol: method tick() location: variable animator of
 * type Object" - real crash log error. Changing DragonEntity's
 * animator field to Object (to fix the actual split-source-set
 * violation) broke DragonEntity's own animator.tick() call in the
 * same file, since Object has no tick() method at all. This small
 * interface lives here in src/main (not client-only), so DragonEntity
 * can cast to it and call tick() without ever referencing the actual
 * client-only DragonAnimator class - DragonAnimator (src/client)
 * implements this interface instead.
 */
public interface ClientTickableAnimator {
    void tick();
}
