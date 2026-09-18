package com.dragonspeech.client.mind;

/**
 * Client-side possession presentation state: which mob's skin this
 * client's player is currently wearing, so PossessedMobRenderMixin can
 * skip rendering that one entity in first person (the borrowed skin
 * would otherwise sit inside the camera). Mirrors MindControlClientState's
 * update pattern; purely cosmetic - the server owns everything real.
 */
public final class PossessionClientState {

    private static int possessedMobId = -1;
    private static boolean active = false;

    private PossessionClientState() {}

    public static void update(int mobEntityId, boolean nowActive) {
        possessedMobId = nowActive ? mobEntityId : -1;
        active = nowActive;
    }

    public static boolean isActive() {
        return active;
    }

    public static boolean isPossessedMob(int entityId) {
        return active && entityId == possessedMobId;
    }
}
