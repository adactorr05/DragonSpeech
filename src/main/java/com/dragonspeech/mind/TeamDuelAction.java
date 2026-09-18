package com.dragonspeech.mind;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/**
 * The action set for TeamMindDuel - matches the design notes' "Attacker
 * Strategies" and "Team Coordination Actions" lists (image 8), scaled
 * down from open-ended strategy names to a fixed, resolvable set the
 * same way DuelAction is for 1v1.
 */
public enum TeamDuelAction implements StringRepresentable {
    // --- Attacker (targets one specific defender member by UUID, except DISRUPT_LINKS which hits the team as a whole).
    ISOLATE,        // attempt to sever one member from the link (design notes: "cut off one defender from the group")
    OVERWHELM,      // heavy Focus damage to one target member
    DISRUPT_LINKS,  // damage the shared Link Strength directly
    FALSE_TARGETS,  // cheaper, weaker attack that also chips Link Strength - "sow confusion"
    WEAR_THEM_DOWN, // cheap Stamina-drain attrition against one member

    // --- Defender team (any connected, non-downed member may act; some target an ally).
    REINFORCE,      // send some of your own Focus to a target ally
    PROTECT,        // shield a target ally from a portion of their next hit
    REVIVE,         // bring a downed ally back with a small amount of Focus
    FOCUS_BURST,    // every connected member spends Stamina for one big combined strike
    RESTORE_FOCUS,  // individual self-restore

    // --- Either side.
    DISENGAGE;

    public static final Codec<TeamDuelAction> CODEC = StringRepresentable.fromEnum(TeamDuelAction::values);

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
