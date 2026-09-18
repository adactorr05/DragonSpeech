package com.dragonspeech.network;

import com.dragonspeech.DragonSpeech;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server -> client: starts/stops the attacker's local possession input forwarding. */
public record MindControlStatePayload(int targetEntityId, boolean active) implements CustomPacketPayload {

    public static final Type<MindControlStatePayload> TYPE = new Type<>(DragonSpeech.id("mind_control_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MindControlStatePayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, MindControlStatePayload::targetEntityId,
        ByteBufCodecs.BOOL, MindControlStatePayload::active,
        MindControlStatePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
