package com.dragonspeech.network;

import com.dragonspeech.DragonSpeech;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client -> server: "send me the current server-tab config + whether I'm
 * allowed to edit it." Sent once when ConfigScreen opens while actually
 * connected to a world/server (never sent from the Title Screen, where
 * there's no connection to send it over at all - see ConfigScreen's own
 * doc). No fields needed - the server already knows who's asking from
 * the connection itself.
 */
public record ConfigRequestPayload() implements CustomPacketPayload {

    public static final Type<ConfigRequestPayload> TYPE = new Type<>(DragonSpeech.id("config_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ConfigRequestPayload> STREAM_CODEC =
            StreamCodec.unit(new ConfigRequestPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
