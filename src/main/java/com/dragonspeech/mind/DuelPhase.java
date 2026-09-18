package com.dragonspeech.mind;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/**
 * The phases a mind duel moves through, in order - matches the design
 * notes' "Phase Flow Recap". A duel can END at any phase (defender
 * repels the attacker, the attacker withdraws, either side is physically
 * interrupted too many times) - it does not have to run the full chain.
 *
 * TRUE_NAME_DOMINATION is reachable from OCCUPIED_MIND the instant the
 * attacker knows the defender's true name -
 * it is a shortcut through resistance, not a phase everyone passes
 * through. Most duels will never reach it.
 */
public enum DuelPhase implements StringRepresentable {
    /** Attacker is trying to establish contact at all. Resolved by ContactResolver before an ActiveMindDuel even exists. */
    CONTACT,
    /** Attacker is pressing against the defender's mental barrier, trying to breach it. */
    DEFENSE_BREACH,
    /** Attacker has broken the barrier and is inside, issuing commands the defender can still resist. */
    OCCUPIED_MIND,
    /** Attacker knows the defender's true name - commands are now enforced with near-total authority. */
    TRUE_NAME_DOMINATION,
    /** Terminal - the duel is over and should be removed from MindDuelManager on the next tick. */
    ENDED;

    public static final Codec<DuelPhase> CODEC = StringRepresentable.fromEnum(DuelPhase::values);

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
