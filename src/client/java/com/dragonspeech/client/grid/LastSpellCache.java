package com.dragonspeech.client.grid;

/**
 * Remembers the last spell submitted this session, so the recast keybind
 * can resend it without opening the grid at all. Client-side convenience
 * only - the server re-verifies every word on every submission exactly as
 * if it were freshly built, so replaying stale JSON is harmless (any
 * no-longer-valid word is simply dropped server-side).
 */
public final class LastSpellCache {

    private static String lastSubmittedJson;

    private LastSpellCache() {}

    public static void remember(String json) {
        lastSubmittedJson = json;
    }

    public static String get() {
        return lastSubmittedJson;
    }
}
