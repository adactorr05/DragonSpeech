package com.dragonspeech.mind;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/**
 * The five resource bars every duel combatant has, and the five cards
 * (one per bar) used to spend them - this is the actual core mechanic
 * now: pick a card (a bar), then click the barrier/a crack directly.
 * The bar you picked drains; how much depends on the bar's own risk
 * profile below. Named "ability" actions from earlier iterations of
 * this system are gone - clicking IS the action, the card just decides
 * which resource pays for it and how it behaves.
 *
 * FOCUS (blue) is deliberately the dangerous one: highest impact per
 * click, highest self-cost per click, AND the only bar that can also be
 * drained by physical damage taken outside the duel entirely (see
 * MindDuelService.onPhysicalDamage). Hitting 0 Focus doesn't just cost
 * you the exchange - it ends the whole encounter immediately, per the
 * user's design: the attacker must cool down before re-entering that
 * specific mind, while a defender who hits 0 Focus gets a real choice
 * (flee, or seize control and become the attacker - see SEIZE_CONTROL).
 */
public enum BarType implements StringRepresentable {
    /** The dangerous one. Big impact, big self-cost, hitting 0 ends the encounter outright. */
    FOCUS(1.6f, 1.0f),
    /** Steady and cheap - the "I can keep doing this all day" bar. */
    STAMINA(0.8f, 1.4f),
    /** Defensive-leaning - strong seals/blocks, moderate cost. */
    WILLPOWER(1.1f, 1.1f),
    /** Heavy hitting/mending, but drains fast - a burst option, not a sustain one. */
    POWER(1.4f, 1.2f),
    /** Cheap and quick - favors a fast clicker who wants to spam small hits. */
    SPEED(0.7f, 0.9f);

    /** Multiplier on how much a single click of this card damages/heals a crack. */
    private final float impactMultiplier;
    /** Multiplier on how much of the bar a single click costs, relative to a flat baseline. */
    private final float costMultiplier;

    BarType(float impactMultiplier, float costMultiplier) {
        this.impactMultiplier = impactMultiplier;
        this.costMultiplier = costMultiplier;
    }

    public float impactMultiplier() {
        return impactMultiplier;
    }

    public float costMultiplier() {
        return costMultiplier;
    }

    public static final Codec<BarType> CODEC = StringRepresentable.fromEnum(BarType::values);

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
