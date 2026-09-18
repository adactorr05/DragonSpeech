package com.dragonspeech.network;

import com.dragonspeech.DragonSpeech;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;

/** Client -> server: request one server-validated Word-of-Words action. */
public record WordOfWordsActionPayload(UUID sessionId, String action, String parameter) implements CustomPacketPayload {
    public static final Type<WordOfWordsActionPayload> TYPE = new Type<>(DragonSpeech.id("word_of_words_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WordOfWordsActionPayload> STREAM_CODEC = StreamCodec.composite(
        UUIDUtil.STREAM_CODEC, WordOfWordsActionPayload::sessionId,
        ByteBufCodecs.stringUtf8(128), WordOfWordsActionPayload::action,
        ByteBufCodecs.stringUtf8(512), WordOfWordsActionPayload::parameter,
        WordOfWordsActionPayload::new
    );
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
