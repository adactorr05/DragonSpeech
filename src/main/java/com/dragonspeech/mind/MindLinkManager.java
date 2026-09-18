package com.dragonspeech.mind;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Tracks standing MindLinks - formed outside combat (design notes:
 * defenders link BEFORE a duel starts, not during one). A player can
 * belong to at most one link at a time, same "no double membership"
 * rule as MindDuelManager enforces for duels themselves.
 *
 * NO CONSENT HANDSHAKE for form() - it links every named player
 * immediately, the same way a party/team command in most mods works.
 * join() is the real player-facing path now - only ever called after
 * the joining player explicitly accepts an invite, see PendingLinkInvites
 * and MindCommands' /mind link invite|accept.
 */
public final class MindLinkManager {

    private static final Map<UUID, MindLink> LINKS_BY_ID = new HashMap<>();
    private static final Map<UUID, UUID> PLAYER_TO_LINK = new HashMap<>();

    private MindLinkManager() {}

    public static MindLink form(List<UUID> playerIds) {
        // Anyone already linked is pulled out of their old link first - no dual membership.
        for (UUID playerId : playerIds) {
            leave(playerId);
        }
        MindLink link = new MindLink(UUID.randomUUID());
        for (UUID playerId : playerIds) {
            link.addMember(playerId);
            PLAYER_TO_LINK.put(playerId, link.linkId());
        }
        LINKS_BY_ID.put(link.linkId(), link);
        return link;
    }

    /**
     * Consent-based alternative to form(): adds `joinerId` to whatever
     * link `inviterId` is currently in, creating a fresh 2-person link
     * first if the inviter wasn't linked to anyone yet. Only ever call
     * this after the joiner has explicitly accepted an invite - see
     * PendingLinkInvites and MindCommands' /mind link invite|accept.
     */
    public static MindLink join(UUID inviterId, UUID joinerId) {
        leave(joinerId);
        UUID existingLinkId = PLAYER_TO_LINK.get(inviterId);
        MindLink link = existingLinkId != null ? LINKS_BY_ID.get(existingLinkId) : null;
        if (link == null) {
            link = new MindLink(UUID.randomUUID());
            link.addMember(inviterId);
            PLAYER_TO_LINK.put(inviterId, link.linkId());
            LINKS_BY_ID.put(link.linkId(), link);
        }
        link.addMember(joinerId);
        PLAYER_TO_LINK.put(joinerId, link.linkId());
        return link;
    }

    public static Optional<MindLink> get(UUID playerId) {
        UUID linkId = PLAYER_TO_LINK.get(playerId);
        return linkId == null ? Optional.empty() : Optional.ofNullable(LINKS_BY_ID.get(linkId));
    }

    /** Removes just this one player from whatever link they're in - disbands the link entirely if that leaves fewer than 2 members. */
    public static void leave(UUID playerId) {
        UUID linkId = PLAYER_TO_LINK.remove(playerId);
        if (linkId == null) {
            return;
        }
        MindLink link = LINKS_BY_ID.get(linkId);
        if (link == null) {
            return;
        }
        link.removeMember(playerId);
        if (link.memberIds().size() < 2) {
            for (UUID remaining : List.copyOf(link.memberIds())) {
                PLAYER_TO_LINK.remove(remaining);
            }
            LINKS_BY_ID.remove(linkId);
        }
    }
}
