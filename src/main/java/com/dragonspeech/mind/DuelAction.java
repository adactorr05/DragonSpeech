package com.dragonspeech.mind;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/**
 * Every action either side can take. MindDuelActionService.resolve() is
 * the only place that decides which of these are legal for a given
 * phase/role - this enum itself makes no claim about when an action is
 * usable.
 *
 * Defense Breach is direct interaction: pick a card (a BarType), then
 * click the barrier/a crack directly. STRIKE_CRACK and SEAL_CRACK are
 * that click, parameterized by which bar pays for it. Attackers send
 * "barType:x:y"; defenders send "barType:crackId".
 */
public enum DuelAction implements StringRepresentable {
    // --- Defense Breach: the entire mechanic is these two, parameterized by BarType + target.
    STRIKE_CRACK, // attacker - click anywhere on the barrier, paying with a chosen bar (param = "barType:x:y")
    SEAL_CRACK,   // defender - seal a specific crack, paying with a chosen bar (param = "barType:crackId")

    // --- Reachable the instant a DEFENDER's Focus bar hits 0 during Defense Breach - a real choice, not an automatic loss.
    SEIZE_CONTROL, // defender only - become the attacker; roles swap and a fresh (partially-damaged) barrier begins

    // --- Occupied Mind: attacker-only issue, defender-only resist.
    ISSUE_COMMAND,
    RESIST,

    // --- Available once the attacker knows the defender's true name.
    SPEAK_TRUE_NAME,

    // --- Either side, any phase.
    DISENGAGE;

    public static final Codec<DuelAction> CODEC = StringRepresentable.fromEnum(DuelAction::values);

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
