package com.dragonspeech.network;

import com.dragonspeech.DragonSpeech;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server -> client: open the tablet reading screen with this content JSON. */
public record OpenTabletPayload(String contentJson) implements CustomPacketPayload {

    public static final Type<OpenTabletPayload> TYPE = new Type<>(DragonSpeech.id("open_tablet"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenTabletPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, OpenTabletPayload::contentJson,
        OpenTabletPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
