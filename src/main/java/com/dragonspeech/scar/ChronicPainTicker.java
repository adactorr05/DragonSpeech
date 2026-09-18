package com.dragonspeech.scar;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import java.util.Random;

/**
 * CHRONIC_PAIN fires unpredictably rather than on a fixed timer -
 * "flares without warning" is the whole point, matching an old wound
 * that never fully heals. Checked once a second; each check is an
 * independent low-probability roll per player with the scar.
 */
public final class ChronicPainTicker {

    private static final int CHECK_INTERVAL_TICKS = 20;
    private static final float CHANCE_PER_CHECK = 0.02f; // ~ once per 50 seconds on average
    private static final Random RANDOM = new Random();

    private static int counter = 0;

    private ChronicPainTicker() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(ChronicPainTicker::tick);
    }

    private static void tick(MinecraftServer server) {
        counter++;
        if (counter < CHECK_INTERVAL_TICKS) {
            return;
        }
        counter = 0;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!ScarAccess.get(player).has(ScarType.CHRONIC_PAIN)) {
                continue;
            }
            if (RANDOM.nextFloat() < CHANCE_PER_CHECK) {
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
                player.hurt(player.damageSources().magic(), 1.0f);
                player.sendSystemMessage(Component.literal("The old wound flares, unbidden."));
            }
        }
    }
}
