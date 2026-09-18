package com.dragonspeech.mob.casting;

import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * "After fighting for a bit, they can learn what does damage and can
 * learn how to bypass their wards" per explicit direction - "this
 * should be another aspect of the ADVANCED shades." Implemented by
 * ShadeEntity (only actually remembering anything if it counts as
 * "advanced" - see that class - so a plain 2-element Shade doesn't get
 * this behavior, only the rarer, more versatile ones do).
 *
 * The learning signal is per-VERB, not per-ward-type: rather than a
 * hardcoded "this ward type blocks that effect category" table (which
 * would need to correctly reverse-engineer exactly how every effect
 * deals its damage), a Shade just remembers "casting THIS specific word
 * at THIS specific target got blocked" empirically - see
 * MobCastExecutor (records which verb was just cast) and
 * MobWards.applyWards (fires the memory the instant that cast's damage
 * gets reduced). MobSpellComposer then deprioritizes - but doesn't
 * permanently forbid, since the target's ward might run out of
 * durability later (see MobWards.WardInstance) - any verb already known
 * to be blocked against the CURRENT target.
 *
 * Deliberately NOT persisted (NBT save/load) - this is short-term
 * tactical memory from an ongoing fight, not a permanent record; it
 * resets on despawn/relog, which is fine and arguably more thematically
 * correct than a Shade never forgetting anything forever.
 */
public interface WardLearner {

    /** Called by MobCastExecutor right before an OFFENSE-intent spell actually executes, so applyWards can attribute a block to the right verb. */
    void setLastCastVerb(ResourceLocation verbId);

    /** Read by MobWards.applyWards at the moment a block happens, to know which verb to attribute it to. */
    ResourceLocation getLastCastVerb();

    /** Called by MobWards.applyWards the instant a cast (identified via setLastCastVerb, above) gets reduced by a ward. */
    void rememberBlocked(UUID targetId, ResourceLocation verbId);

    /** Checked by MobSpellComposer when picking which verb to try first against a given target. */
    boolean isKnownBlocked(UUID targetId, ResourceLocation verbId);
}
