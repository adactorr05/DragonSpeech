package com.dragonspeech.network;

import com.dragonspeech.DragonSpeech;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client -> server: "I believe <wordId> means <chosenMeaning>." Untrusted - the server re-reads the held tablet and checks against the real registry meaning. */
public record TranslateAttemptPayload(String wordId, String chosenMeaning) implements CustomPacketPayload {

    public static final Type<TranslateAttemptPayload> TYPE = new Type<>(DragonSpeech.id("translate_attempt"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TranslateAttemptPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, TranslateAttemptPayload::wordId,
        ByteBufCodecs.STRING_UTF8, TranslateAttemptPayload::chosenMeaning,
        TranslateAttemptPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
