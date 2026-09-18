package com.dragonspeech.channel;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

/**
 * Pulses every 20 server ticks (once per second) rather than every tick -
 * keeps costPerTick values on a human-legible timescale instead of needing
 * 20-per-second granularity.
 */
public final class ChannelTickHandler {

    private static final int TICKS_PER_PULSE = 20;
    private static int tickCounter = 0;

    private ChannelTickHandler() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tickCounter++;
            if (tickCounter >= TICKS_PER_PULSE) {
                tickCounter = 0;
                ChannelManager.pulseAll(server);
            }
        });
    }
}
