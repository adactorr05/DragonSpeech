package com.dragonspeech.network;

import com.dragonspeech.DragonSpeech;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client -> server: "I think the word is ___." Untrusted input - GuessResolver re-checks everything server-side. */
public record GuessSubmitPayload(String candidate) implements CustomPacketPayload {

    public static final Type<GuessSubmitPayload> TYPE = new Type<>(DragonSpeech.id("guess_submit"));

    public static final StreamCodec<RegistryFriendlyByteBuf, GuessSubmitPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, GuessSubmitPayload::candidate,
        GuessSubmitPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
