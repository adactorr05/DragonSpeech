package com.dragonspeech.network;

import com.dragonspeech.DragonSpeech;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client -> server: "re-read config/dragonspeech/sentience.json now,
 * without a restart." The button on the config GUI's Server tab.
 * Re-validated server-side against hasPermissions(4) exactly like
 * ConfigUpdatePayload - never trusted just because the button is
 * hidden client-side for non-admins.
 */
public record ReloadSentiencePayload() implements CustomPacketPayload {

    public static final Type<ReloadSentiencePayload> TYPE = new Type<>(DragonSpeech.id("reload_sentience"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ReloadSentiencePayload> STREAM_CODEC =
        StreamCodec.unit(new ReloadSentiencePayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
