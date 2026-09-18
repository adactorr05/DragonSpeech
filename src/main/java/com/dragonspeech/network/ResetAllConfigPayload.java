package com.dragonspeech.network;

import com.dragonspeech.DragonSpeech;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client -> server: "Reset All Configs" (the universal reset) on the config GUI - resets EVERY
 * server-side area at once: the 10 GUI-added Server-tab fields, all 3 difficulty tiers' tuning, and
 * every sentience override. Does NOT reset Magic Difficulty's own active selection (which tier is
 * currently chosen) - only what each tier means and the extra settings layered on top.
 *
 * Client-only settings (DragonSpeechClientConfig) are NOT part of this payload at all - those reset
 * locally, instantly, with no network round trip needed, since they're never server-side data to
 * begin with (see ConfigScreen#resetAllConfigs()).
 *
 * Re-validated server-side against hasPermissions(4), same as every other Server-tab edit.
 */
public record ResetAllConfigPayload() implements CustomPacketPayload {

    public static final Type<ResetAllConfigPayload> TYPE = new Type<>(DragonSpeech.id("reset_all_config"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ResetAllConfigPayload> STREAM_CODEC =
        StreamCodec.unit(new ResetAllConfigPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
