package com.dragonspeech.mind;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Registry of in-flight Contact attempts - at most one per attacker, mirroring every other "no double engagement" registry in this system. */
public final class PendingContactManager {

    private static final Map<UUID, PendingContact> BY_ATTACKER = new HashMap<>();

    private PendingContactManager() {}

    public static boolean isPending(UUID attackerId) {
        return BY_ATTACKER.containsKey(attackerId);
    }

    public static Optional<PendingContact> get(UUID attackerId) {
        return Optional.ofNullable(BY_ATTACKER.get(attackerId));
    }

    public static void start(PendingContact contact) {
        BY_ATTACKER.put(contact.attackerId(), contact);
    }

    public static void clear(UUID attackerId) {
        BY_ATTACKER.remove(attackerId);
    }

    public static java.util.List<PendingContact> allPending() {
        return java.util.List.copyOf(BY_ATTACKER.values());
    }
}
