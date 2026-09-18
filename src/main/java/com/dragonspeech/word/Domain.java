package com.dragonspeech.word;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/**
 * The elemental or conceptual school a word belongs to. A player's
 * Attunement is tracked per-domain, so casting more in a domain makes
 * future casts in that same domain more efficient.
 *
 * This is a fixed enum for now to keep Phase 1 simple. If you want
 * other datapacks/addons to be able to register entirely new domains
 * later, this should become a dynamic registry instead - flag that as
 * a possible Phase 2+ refactor rather than something to solve today.
 */
public enum Domain implements StringRepresentable {
    FIRE,
    WATER,
    EARTH,
    AIR,
    LIFE,
    DEATH,
    MIND,
    FORCE,
    MOTION,
    BINDING,
    TRUTH,
    /** Advanced reality domains are kept separate so mastery in one dangerous art does not cheaply train another. */
    TIME,
    GRAVITY,
    FATE,
    VOID,
    /** Materials and tools/weapons a hurling names - added for the thrown-weapon projectile system. See com.dragonspeech.weapon. */
    WEAPON;

    public static final Codec<Domain> CODEC = StringRepresentable.fromEnum(Domain::values);

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
