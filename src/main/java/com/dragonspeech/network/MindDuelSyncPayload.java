package com.dragonspeech.network;

import com.dragonspeech.DragonSpeech;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client: a snapshot of the receiving player's current mind
 * duel, as JSON (phase, both sides' Focus/Stamina/Willpower, breach
 * integrity, control advantage). One generic payload for every phase
 * rather than one per phase - the client screen for whichever phase is
 * active reads whichever fields it needs and ignores the rest, so
 * adding richer per-phase detail later never requires a new payload type.
 * An empty string means "no active duel - close the screen if open."
 */
public record MindDuelSyncPayload(String duelJson) implements CustomPacketPayload {

    public static final Type<MindDuelSyncPayload> TYPE = new Type<>(DragonSpeech.id("mind_duel_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MindDuelSyncPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, MindDuelSyncPayload::duelJson,
        MindDuelSyncPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
