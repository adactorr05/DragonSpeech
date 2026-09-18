package com.dragonspeech.mind;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/**
 * How resistant a mind is to being reached, entered, or read, before any
 * player training is factored in. This is the mob-side half of Phase 6's
 * "not every mind is defended the same way" goal (see design notes,
 * point 9, "Passive Mind Strength") - a villager and a warden should never
 * feel like the same fight.
 *
 * Players do NOT sit on this scale directly - their fortitude is grown
 * (Attunement in the MIND domain, the hugvarna skill, Discipline) rather
 * than assigned. This enum exists so MOBS have a mind at all without
 * needing per-entity hand tuning; see MindFortitudeService for how a
 * tier turns into the actual MindCombatant pool an attacker faces.
 *
 * baseFortitude is a flat resistance score subtracted from an attacker's
 * roll during ContactResolver and DefenseBreachResolver - NOT a
 * multiplier, so it composes cleanly with a player's own trained bonus.
 */
public enum SentienceTier implements StringRepresentable {
    /** No mind to speak of - contact is simply impossible. Zombies, most constructs, arrows. */
    MINDLESS(Integer.MIN_VALUE, 0f, 0f, "There is nothing here to reach."),
    /** Barely a mind at all - passive farm/wild animals (chickens, cows, pigs, sheep, etc). Fear and grazing, nothing more - a duel against one of these should be close to a formality. */
    TRIVIAL(1, 15f, 15f, "Barely a mind at all - fear and grazing, nothing more."),
    /** Pure animal instinct - reachable, but there is almost nothing to find inside. Hostile mobs without real intent (spiders, most monsters below thinking-monster tier). */
    INSTINCTUAL(5, 40f, 40f, "A flicker of hunger and fear. Nothing more."),
    /** A simple working mind - villagers, most humanoid mobs. */
    SIMPLE(15, 60f, 55f, "An ordinary mind, unguarded and unremarkable."),
    /** A trained or unusually alert mind - guards, illagers, veteran mobs. */
    TRAINED(30, 90f, 80f, "This mind knows, at least a little, that minds can be attacked."),
    /** A mind shaped by real discipline - elves, skilled casters. */
    DISCIPLINED(50, 130f, 110f, "Layered, deliberate defenses. Someone taught this mind to guard itself."),
    /** Something too alien to read cleanly - endermen, the warden. Chaotic rather than strong. */
    CHAOTIC(45, 100f, 160f, "The shape of this mind will not hold still long enough to be read."),
    /** Ancient, vast minds - dragons. See design notes point 14: no walls, no maze, just scale. */
    DRAGON(120, 400f, 400f, "You do not enter this mind. You fall into it.");

    private final int baseFortitude;
    private final float baseFocus;
    private final float baseStamina;
    private final String flavor;

    SentienceTier(int baseFortitude, float baseFocus, float baseStamina, String flavor) {
        this.baseFortitude = baseFortitude;
        this.baseFocus = baseFocus;
        this.baseStamina = baseStamina;
        this.flavor = flavor;
    }

    public int baseFortitude() {
        return baseFortitude;
    }

    public float baseFocus() {
        return baseFocus;
    }

    public float baseStamina() {
        return baseStamina;
    }

    public String flavor() {
        return flavor;
    }

    public boolean canBeDueled() {
        return this != MINDLESS;
    }

    /**
     * Whether a mob at this tier is sentient/capable enough to actually
     * fight or defend for itself in a duel - choosing actions (Reinforce,
     * Seal Mind, Assault, Issue Command, and so on) rather than just
     * sitting there as a passive stat pool for a player to whittle down.
     * See MobMindCombatAI, which is the thing that actually acts on this.
     *
     * INSTINCTUAL and SIMPLE minds (animals, villagers, most ordinary
     * humanoids) are reachable and have real stats, but there's no one
     * "home" to make deliberate tactical choices - a cow doesn't Seal
     * Mind. TRAINED and above do: guards, illagers, elves, endermen, the
     * warden, and anything as vast as a dragon are all sentient/capable
     * enough, or simply powerful enough, to actually fight back.
     */
    public boolean canActInDuel() {
        return this == TRAINED || this == DISCIPLINED || this == CHAOTIC || this == DRAGON;
    }

    public static final Codec<SentienceTier> CODEC = StringRepresentable.fromEnum(SentienceTier::values);

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
