package com.dragonspeech.mind;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * "Alone, you may be strong. Together, you are unbreakable." A Mind Link
 * is a standing bond between players, formed OUTSIDE combat (see
 * MindLinkManager.form()) and consulted the moment someone reaches out
 * to touch one of its members (see ContactResolver) - if the target is
 * linked, the resulting duel is a TeamMindDuel instead of a plain
 * ActiveMindDuel, with every linked member drawn in as a co-defender.
 *
 * linkStrength (0-100) is the team's own shared resource, separate from
 * any individual member's Focus/Stamina - see TeamMindDuel and the
 * design notes' own "Link Strength" meter. It falls when the attacker
 * uses DISRUPT_LINKS or ISOLATE; a member cut off (isolated) stops
 * benefiting from team actions like Reinforce/Protect until the link
 * recovers or they're re-linked.
 */
public final class MindLink {

    private final UUID linkId;
    private final Set<UUID> memberIds = new LinkedHashSet<>();
    private final Set<UUID> isolatedMembers = new LinkedHashSet<>();
    private float linkStrength = 100f;

    public MindLink(UUID linkId) {
        this.linkId = linkId;
    }

    public UUID linkId() {
        return linkId;
    }

    public Set<UUID> memberIds() {
        return memberIds;
    }

    public void addMember(UUID playerId) {
        memberIds.add(playerId);
    }

    public void removeMember(UUID playerId) {
        memberIds.remove(playerId);
        isolatedMembers.remove(playerId);
    }

    public float linkStrength() {
        return linkStrength;
    }

    public void damageLinkStrength(float amount) {
        linkStrength = Math.max(0f, linkStrength - Math.max(0f, amount));
    }

    public void repairLinkStrength(float amount) {
        linkStrength = Math.min(100f, linkStrength + Math.max(0f, amount));
    }

    public boolean isIsolated(UUID playerId) {
        return isolatedMembers.contains(playerId);
    }

    public void isolate(UUID playerId) {
        isolatedMembers.add(playerId);
    }

    public void reconnect(UUID playerId) {
        isolatedMembers.remove(playerId);
    }

    /** Members still able to benefit from / contribute to team actions. */
    public Set<UUID> connectedMembers() {
        Set<UUID> connected = new LinkedHashSet<>(memberIds);
        connected.removeAll(isolatedMembers);
        return connected;
    }
}
