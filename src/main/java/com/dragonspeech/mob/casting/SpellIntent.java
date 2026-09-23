package com.dragonspeech.mob.casting;

import java.util.Set;

/**
 * What a spellcasting mob is trying to accomplish right now, decided by its AI
 * (MobSpellCastGoal) BEFORE any word is chosen. MobSpellComposer only ever
 * picks a known VERB whose effect_handler id falls in the matching set below -
 * this is the same "fixed, compiled boundary" principle Word.java's
 * effect_handler field already enforces between a word and what it can do;
 * this enum applies that same idea one level up, so mob AI can ask for
 * "something offensive" without ever being able to name an arbitrary effect.
 *
 * All twelve effect ids MobEffectExecutor implements are now covered here -
 * hurl_block joins OFFENSE (it deals direct entity damage, same as
 * BlockThrowEffectHandler), pillar joins MOBILITY (rising into the air is
 * fundamentally a reposition, same read PillarEffectHandler's own doc gives
 * it), and sunder/shape_block join UTILITY alongside wall (all three are
 * block manipulation, not entity-targeted). The only effect_handler ids left
 * out entirely are the player-inventory-only ones (possess, resurrect,
 * charge_item, apply_enchant, the ward-binding family, etc.) - see
 * MobEffectExecutor's class comment for why those stay out of scope.
 *
 * "push" was REMOVED from CROWD_CONTROL for a while (mobs were knocking
 * targets clean out of MobKeepDistanceGoal's fighting range constantly),
 * then RESTORED per explicit follow-up direction: excluding it entirely
 * silently gutted Water's and Air's whole domain identity for Shades
 * ("I don't tend to see much lightning, or water magic") - Water's own
 * signature verbs (vatnhrer/streyma/flodbinda) and half of Air's
 * (blasa/vindkast) are ALL push-effect, so with push uncastable those
 * two domains contributed nothing beyond what SHADE_SHARED's baseline
 * already fakes (kaldna's freeze already reads as "ice" regardless of
 * whether Water got rolled). The actual range-breaking problem was
 * push's POWER, not its existence - see MobEffectExecutor's "push" case,
 * now a light shove (~0.35 base) rather than a launch (previously 1.2).
 */
public enum SpellIntent {
    OFFENSE(Set.of("shock", "ignite", "freeze", "poison", "hurl_block", "danger_word")),
    CROWD_CONTROL(Set.of("petrify", "confuse", "push")),
    MOBILITY(Set.of("teleport", "lift", "pillar")),
    SELF_HEAL(Set.of("heal")),
    SUMMON_HELP(Set.of("summon")),
    UTILITY(Set.of("wall", "sunder", "shape_block"));

    private final Set<String> effectIds;

    SpellIntent(Set<String> effectIds) {
        this.effectIds = effectIds;
    }

    /** Whether a verb whose effect_handler has this path may be used to satisfy this intent. */
    public boolean accepts(String effectHandlerPath) {
        return effectIds.contains(effectHandlerPath);
    }
}
