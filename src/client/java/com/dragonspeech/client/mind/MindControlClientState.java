package com.dragonspeech.client.mind;

import com.dragonspeech.network.MindControlInputPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

/** Client-only possession input bridge. The server owns the actual movement. */
public final class MindControlClientState {

    private static boolean active;
    private static int targetEntityId = -1;
    private static int tickCounter;
    private static String lastDuelJson;

    private MindControlClientState() {}

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(MindControlClientState::tick);
    }

    public static boolean isActive() {
        return active;
    }

    /** Called from the MindDuelSyncPayload receiver every time one arrives, control-active or not - keeps a reopen-ready copy without needing a fresh round-trip to the server. */
    public static void cacheDuelJson(String json) {
        lastDuelJson = json;
    }

    public static String lastDuelJson() {
        return lastDuelJson;
    }

    public static void update(int targetId, boolean isActive) {
        Minecraft client = Minecraft.getInstance();
        active = isActive;
        targetEntityId = isActive ? targetId : -1;
        tickCounter = 0;
        if (isActive && client.screen instanceof MindDuelScreen) {
            client.setScreen(null);
        }
    }

    private static void tick(Minecraft client) {
        if (!active || client.player == null || client.level == null || targetEntityId < 0) {
            return;
        }
        float yaw = client.player.getYRot();
        float pitch = client.player.getXRot();

        int flags = 0;
        if (client.options.keyUp.isDown()) flags |= MindControlInputPayload.FORWARD;
        if (client.options.keyDown.isDown()) flags |= MindControlInputPayload.BACK;
        if (client.options.keyLeft.isDown()) flags |= MindControlInputPayload.LEFT;
        if (client.options.keyRight.isDown()) flags |= MindControlInputPayload.RIGHT;
        if (client.options.keyJump.isDown()) flags |= MindControlInputPayload.JUMP;
        if (client.options.keyShift.isDown()) flags |= MindControlInputPayload.SNEAK;
        if (client.options.keyAttack.isDown()) flags |= MindControlInputPayload.ATTACK;
        if (client.options.keyUse.isDown()) flags |= MindControlInputPayload.USE;

        // Every tick keeps movement responsive; an idle heartbeat every few ticks keeps rotation in sync.
        if (flags != 0 || tickCounter++ % 4 == 0) {
            ClientPlayNetworking.send(new MindControlInputPayload(flags, yaw, pitch));
        }
    }
}
