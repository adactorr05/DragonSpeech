package com.dragonspeech.network;

import com.dragonspeech.DragonSpeech;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client -> server: "cast whatever is currently in my grid." Also a JSON
 * blob for the same reasons as KnownWordsSyncPayload - see that file's
 * comment. The JSON here is a flat object of {slotName: wordId}.
 *
 * SECURITY NOTE: everything in this payload is untrusted input. The
 * server (CastRequestHandler) re-verifies every word ID against that
 * player's actual server-side vocabulary before any of it counts for
 * anything - a modified client claiming a word it hasn't discovered is
 * simply ignored.
 */
public record CastGridSubmitPayload(String assignmentsJson) implements CustomPacketPayload {

    public static final Type<CastGridSubmitPayload> TYPE = new Type<>(DragonSpeech.id("cast_grid_submit"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CastGridSubmitPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, CastGridSubmitPayload::assignmentsJson,
        CastGridSubmitPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
