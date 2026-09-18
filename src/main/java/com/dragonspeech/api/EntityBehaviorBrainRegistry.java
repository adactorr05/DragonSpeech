package com.dragonspeech.api;

import com.dragonspeech.DragonSpeech;

/**
 * BASE MOD - where an addon registers its {@link EntityBehaviorBrain}, from
 * its own main/server entrypoint (not client - see EntityBehaviorBrain's
 * own doc on why this is server-only). One slot, same reasoning as
 * MindDuelBrainRegistry: registering a second brain while one is active
 * logs a warning and keeps the first, rather than two AI addons silently
 * fighting over the same entities.
 *
 * MobSpellCastGoal checks {@link #get()} once per cast attempt and falls
 * back to its own built-in heuristic whenever this returns null or the
 * registered brain's handles() returns false for that entity.
 */
public final class EntityBehaviorBrainRegistry {

    private static EntityBehaviorBrain active;

    private EntityBehaviorBrainRegistry() {}

    public static void register(EntityBehaviorBrain brain) {
        if (brain == null) {
            return;
        }
        if (active != null) {
            DragonSpeech.LOGGER.warn("[DragonSpeech] An EntityBehaviorBrain is already registered ({}) - ignoring a second registration ({}).",
                    active.getClass().getName(), brain.getClass().getName());
            return;
        }
        active = brain;
        DragonSpeech.LOGGER.info("[DragonSpeech] EntityBehaviorBrain registered: {} - spellcasting decisions for entities it accepts now come from this addon instead of the built-in heuristic.", brain.getClass().getName());
    }

    public static void unregister(EntityBehaviorBrain brain) {
        if (active == brain) {
            active = null;
            DragonSpeech.LOGGER.info("[DragonSpeech] EntityBehaviorBrain unregistered - spellcasting decisions reverting to the built-in heuristic.");
        }
    }

    public static EntityBehaviorBrain get() {
        return active;
    }

    public static boolean isPresent() {
        return active != null;
    }
}
