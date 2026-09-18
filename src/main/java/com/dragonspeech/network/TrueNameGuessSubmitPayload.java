package com.dragonspeech.network;

import com.dragonspeech.DragonSpeech;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;

/** Client -> server: submit a full assembled name guess for a target from the guessing screen. See TrueNameProgressService.submitGuess. */
public record TrueNameGuessSubmitPayload(UUID targetId, String guess) implements CustomPacketPayload {

    public static final Type<TrueNameGuessSubmitPayload> TYPE = new Type<>(DragonSpeech.id("true_name_guess_submit"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TrueNameGuessSubmitPayload> STREAM_CODEC = StreamCodec.composite(
        UUIDUtil.STREAM_CODEC, TrueNameGuessSubmitPayload::targetId,
        ByteBufCodecs.STRING_UTF8, TrueNameGuessSubmitPayload::guess,
        TrueNameGuessSubmitPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
