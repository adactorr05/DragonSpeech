package com.dragonspeech.network;

import com.dragonspeech.DragonSpeech;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server -> client: current/max stamina for the HUD bar. Display-only - the server remains the single authority on the real values. */
public record StaminaSyncPayload(float stamina, float maxStamina) implements CustomPacketPayload {

    public static final Type<StaminaSyncPayload> TYPE = new Type<>(DragonSpeech.id("stamina_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, StaminaSyncPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.FLOAT, StaminaSyncPayload::stamina,
        ByteBufCodecs.FLOAT, StaminaSyncPayload::maxStamina,
        StaminaSyncPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
