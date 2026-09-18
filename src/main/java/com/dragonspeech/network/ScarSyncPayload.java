package com.dragonspeech.network;

import com.dragonspeech.DragonSpeech;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server -> client: the receiving player's own active scars, pre-described (server already knows word names the client might not), for the grimoire's Scars & Wards panel. */
public record ScarSyncPayload(String descriptionsJson) implements CustomPacketPayload {

    public static final Type<ScarSyncPayload> TYPE = new Type<>(DragonSpeech.id("scar_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ScarSyncPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, ScarSyncPayload::descriptionsJson,
        ScarSyncPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
