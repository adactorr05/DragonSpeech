package com.dragonspeech.network;

import com.dragonspeech.DragonSpeech;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client -> server: one DuelAction (by serialized name, e.g. "pressure",
 * "reinforce") in whatever duel this player is currently part of. No duel
 * id needed - MindDuelManager allows at most one duel per participant.
 *
 * param is only meaningful for ISSUE_COMMAND right now - it names which
 * CommandEffectRegistry entry to use (e.g. "drop_weapon"). Every other
 * action ignores it; send an empty string for those.
 */
public record MindDuelActionPayload(String action, String param) implements CustomPacketPayload {

    public static final Type<MindDuelActionPayload> TYPE = new Type<>(DragonSpeech.id("mind_duel_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MindDuelActionPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, MindDuelActionPayload::action,
        ByteBufCodecs.STRING_UTF8, MindDuelActionPayload::param,
        MindDuelActionPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
