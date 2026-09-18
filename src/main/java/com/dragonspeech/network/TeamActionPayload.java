package com.dragonspeech.network;

import com.dragonspeech.DragonSpeech;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.Optional;
import java.util.UUID;

/** Client -> server: one TeamDuelAction (by serialized name) in whatever team duel this player is currently part of, optionally targeting another member by UUID. */
public record TeamActionPayload(String action, Optional<UUID> targetId) implements CustomPacketPayload {

    public static final Type<TeamActionPayload> TYPE = new Type<>(DragonSpeech.id("team_duel_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TeamActionPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, TeamActionPayload::action,
        ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), TeamActionPayload::targetId,
        TeamActionPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
