package com.dragonspeech.api;

import com.dragonspeech.DragonSpeech;

/**
 * Where an addon registers its {@link MindDuelBrain}, typically from its own
 * mod initializer (main/server entrypoint - NOT client, see MindDuelBrain's
 * own doc on why this is server-only). One slot: registering a second brain
 * while one is already active logs a warning and keeps the FIRST one,
 * rather than silently overwriting it - two AI addons fighting over the
 * same entities with no error at all would be a much worse failure mode
 * than a loud, obvious "someone else already claimed this" warning.
 *
 * MobMindCombatAI checks {@link #get()} once per entity per pulse and falls
 * back to its own built-in heuristic whenever this returns null (nothing
 * registered) OR the registered brain's handles() returns false for that
 * entity - "overwrites the sentience [AI]" from the addon's own framing
 * means exactly this: while a brain is registered and accepts a given
 * entity, the base mod's own per-tick heuristic (which is itself largely
 * driven by SentienceTier and MindFortitudeService.REACTION_OVERRIDES)
 * simply never runs for that entity at all - the brain's decision is used
 * in its place, every pulse it chooses to act.
 */
public final class MindDuelBrainRegistry {

    private static MindDuelBrain active;

    private MindDuelBrainRegistry() {}

    public static void register(MindDuelBrain brain) {
        if (brain == null) {
            return;
        }
        if (active != null) {
            DragonSpeech.LOGGER.warn("[DragonSpeech] A MindDuelBrain is already registered ({}) - ignoring a second registration ({}). Only one AI addon can drive mind-duel behavior at a time.",
                    active.getClass().getName(), brain.getClass().getName());
            return;
        }
        active = brain;
        DragonSpeech.LOGGER.info("[DragonSpeech] MindDuelBrain registered: {} - mind-duel AI for entities it accepts now comes from this addon instead of the built-in heuristic.", brain.getClass().getName());
    }

    /** Lets an addon give control back to the base mod's own heuristic without a server restart (e.g. its own "disable" toggle). */
    public static void unregister(MindDuelBrain brain) {
        if (active == brain) {
            active = null;
            DragonSpeech.LOGGER.info("[DragonSpeech] MindDuelBrain unregistered - mind-duel AI reverting to the built-in heuristic.");
        }
    }

    public static MindDuelBrain get() {
        return active;
    }

    public static boolean isPresent() {
        return active != null;
    }
}
