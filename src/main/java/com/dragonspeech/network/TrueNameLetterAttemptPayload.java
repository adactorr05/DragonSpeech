package com.dragonspeech.network;

import com.dragonspeech.DragonSpeech;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;

/** Client -> server: "cast a mind-word from the guessing screen to attempt a letter of this target's true name." See TrueNameProgressService.attemptLetter. */
public record TrueNameLetterAttemptPayload(UUID targetId) implements CustomPacketPayload {

    public static final Type<TrueNameLetterAttemptPayload> TYPE = new Type<>(DragonSpeech.id("true_name_letter_attempt"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TrueNameLetterAttemptPayload> STREAM_CODEC = StreamCodec.composite(
        UUIDUtil.STREAM_CODEC, TrueNameLetterAttemptPayload::targetId,
        TrueNameLetterAttemptPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
