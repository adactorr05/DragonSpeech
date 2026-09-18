package com.dragonspeech.network;

import com.dragonspeech.DragonSpeech;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server -> client: a snapshot of the receiving player's current TeamMindDuel, as JSON. Empty string means "no active team duel - close the screen if open." */
public record TeamMindDuelSyncPayload(String duelJson) implements CustomPacketPayload {

    public static final Type<TeamMindDuelSyncPayload> TYPE = new Type<>(DragonSpeech.id("team_mind_duel_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TeamMindDuelSyncPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, TeamMindDuelSyncPayload::duelJson,
        TeamMindDuelSyncPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
