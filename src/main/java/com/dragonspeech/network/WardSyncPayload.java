package com.dragonspeech.network;

import com.dragonspeech.DragonSpeech;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server -> client: the receiving player's OWN wards (type + strength fraction), for the caster-only ring visuals. Never sent about other players - wards on others stay invisible per the design. */
public record WardSyncPayload(String wardsJson) implements CustomPacketPayload {

    public static final Type<WardSyncPayload> TYPE = new Type<>(DragonSpeech.id("ward_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, WardSyncPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, WardSyncPayload::wardsJson,
        WardSyncPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
