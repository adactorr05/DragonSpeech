package com.dragonspeech.client.hud;

/** Display-only mirror of the server's stamina values, fed by StaminaSyncPayload. */
public final class ClientStaminaCache {

    private static float stamina = 0f;
    private static float maxStamina = 100f;

    private ClientStaminaCache() {}

    public static void update(float newStamina, float newMax) {
        stamina = newStamina;
        maxStamina = newMax;
    }

    public static float stamina() {
        return stamina;
    }

    public static float maxStamina() {
        return maxStamina;
    }
}
