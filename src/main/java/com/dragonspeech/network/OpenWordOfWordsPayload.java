package com.dragonspeech.network;

import com.dragonspeech.DragonSpeech;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;

/** Server -> client: opens or refreshes the Word-of-Words reality editor. */
public record OpenWordOfWordsPayload(UUID sessionId, String contextJson) implements CustomPacketPayload {
    public static final Type<OpenWordOfWordsPayload> TYPE = new Type<>(DragonSpeech.id("open_word_of_words"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenWordOfWordsPayload> STREAM_CODEC = StreamCodec.composite(
        UUIDUtil.STREAM_CODEC, OpenWordOfWordsPayload::sessionId,
        ByteBufCodecs.stringUtf8(65_536), OpenWordOfWordsPayload::contextJson,
        OpenWordOfWordsPayload::new
    );
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
