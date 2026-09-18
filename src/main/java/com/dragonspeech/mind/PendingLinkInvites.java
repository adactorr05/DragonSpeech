package com.dragonspeech.mind;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * One pending invite per potential joiner (a fresh invite overwrites
 * whatever they hadn't yet responded to - simplest possible rule, no
 * queueing). MindCommands' /mind link invite creates these; /mind link
 * accept consumes one and calls MindLinkManager.join().
 */
public final class PendingLinkInvites {

    private static final Map<UUID, UUID> INVITES = new HashMap<>(); // invitee -> inviter

    private PendingLinkInvites() {}

    public static void invite(UUID inviterId, UUID inviteeId) {
        INVITES.put(inviteeId, inviterId);
    }

    /** Returns the inviter's id and clears the invite, or null if there was none. */
    public static UUID consume(UUID inviteeId) {
        return INVITES.remove(inviteeId);
    }

    public static boolean hasInviteFrom(UUID inviteeId, UUID inviterId) {
        return inviterId.equals(INVITES.get(inviteeId));
    }
}
