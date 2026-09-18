package com.dragonspeech.channel;

import com.dragonspeech.effect.EffectInvocation;
import com.dragonspeech.effect.EffectTarget;
import com.dragonspeech.effect.EffectHandler;
import com.dragonspeech.stamina.DrainResolver;
import com.dragonspeech.stamina.DrainResult;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks every player's currently-held channeled spell - at most one per
 * player; starting a new channel stops whatever was already open first.
 * Pulsed roughly once per second by ChannelTickHandler, which drains
 * costPerTick and calls the handler's apply() again for as long as the
 * channel stays open and both caster and target remain valid.
 *
 * Unlike CastExecutor, there's no upfront lump-sum payment here - a
 * channel costs nothing to start and only ever drains per pulse, which is
 * why stopping early (via the "letta" control word) never needs a refund:
 * you only ever paid for time that actually elapsed.
 */
public final class ChannelManager {

    private static final Map<UUID, ActiveChannel> ACTIVE_CHANNELS = new HashMap<>();

    private ChannelManager() {}

    public static void start(ServerPlayer caster, EffectHandler handler, EffectInvocation invocation, float costPerTick) {
        stop(caster, null); // silently replace any existing channel - the flavor message below covers it
        ACTIVE_CHANNELS.put(caster.getUUID(), new ActiveChannel(caster.getUUID(), handler, invocation, costPerTick, caster.level().getGameTime()));
        caster.sendSystemMessage(Component.literal("The word holds, humming, waiting for your will."));
    }

    /** Called by the CONTROL word ("letta"). Returns false if there was nothing to stop. */
    public static boolean stop(ServerPlayer caster, String message) {
        ActiveChannel removed = ACTIVE_CHANNELS.remove(caster.getUUID());
        if (removed != null && message != null) {
            caster.sendSystemMessage(Component.literal(message));
        }
        return removed != null;
    }

    public static boolean hasActiveChannel(ServerPlayer caster) {
        return ACTIVE_CHANNELS.containsKey(caster.getUUID());
    }

    /** Advances every active channel by one pulse. Call roughly once per second, not every tick. */
    public static void pulseAll(MinecraftServer server) {
        if (ACTIVE_CHANNELS.isEmpty()) {
            return;
        }

        for (UUID casterId : List.copyOf(ACTIVE_CHANNELS.keySet())) {
            ActiveChannel channel = ACTIVE_CHANNELS.get(casterId);
            if (channel == null) {
                continue;
            }

            ServerPlayer caster = server.getPlayerList().getPlayer(casterId);
            if (caster == null || !caster.isAlive()) {
                ACTIVE_CHANNELS.remove(casterId);
                continue;
            }

            if (!targetsStillValid(channel.invocation())) {
                ACTIVE_CHANNELS.remove(casterId);
                caster.sendSystemMessage(Component.literal("The word finds nothing left to hold, and fades."));
                continue;
            }

            // Cost is recomputed LIVE each pulse rather than locked in at
            // start: as the held target climbs or fights harder, each
            // pulse prices the work as it stands now. On top of that, a
            // duration surcharge grows the longer the working is held -
            // sustaining a channel tires the caster progressively, it
            // never settles into being free.
            long secondsHeld = Math.max(0, (caster.level().getGameTime() - channel.startGameTime()) / 20);
            float durationSurcharge = 1.0f + secondsHeld * 0.06f;
            // start() already resolved the correct pulse price.  Generic stodugt channels pass a
            // derived one-shot fraction here; calling handler.costPerTick() again would return 0
            // for most ordinary spell handlers and accidentally make sustained magic free.
            float pulseCost = channel.costPerTick() * durationSurcharge;

            DrainResult drain = DrainResolver.applyDrain(caster, pulseCost);
            if (!drain.succeeded()) {
                ACTIVE_CHANNELS.remove(casterId);
                continue; // DrainResolver already applied the overdraft collapse feedback
            }

            channel.handler().apply(channel.invocation());
        }
    }

    private static boolean targetsStillValid(EffectInvocation invocation) {
        for (EffectTarget target : invocation.targets()) {
            if (target instanceof EffectTarget.OfEntity(Entity entity) && !entity.isAlive()) {
                return false;
            }
        }
        return true;
    }
}
