package com.dragonspeech.network;

import com.dragonspeech.DragonSpeech;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client -> server: "toggle favorite on this word." Server flips whatever the real current state is - never trusts a client-claimed new value. */
public record ToggleFavoritePayload(String wordId) implements CustomPacketPayload {

    public static final Type<ToggleFavoritePayload> TYPE = new Type<>(DragonSpeech.id("toggle_favorite"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ToggleFavoritePayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, ToggleFavoritePayload::wordId,
        ToggleFavoritePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
