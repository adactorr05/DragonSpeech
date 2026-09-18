package com.dragonspeech.network;

import com.dragonspeech.DragonSpeech;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client -> server: apply one entity's sentience override from the config GUI's Sentience Editor.
 * Scoped to a SINGLE entity per packet (rather than the "send the whole state" convention the rest
 * of the config GUI uses) because the editor's list can realistically cover hundreds of entity types
 * (vanilla + every installed mod's) - sending all of them on every single edit would be wasteful in a
 * way the small, fixed set of fields on ConfigUpdatePayload never has to worry about.
 *
 * hasTier/hasReactionPower distinguish "leave this half alone" from "clear this half back to
 * default" - tier/reactionPower are meaningless when their matching has* flag is false, and a
 * receiver must check the flag first rather than inferring intent from a sentinel value.
 *
 * Re-validated server-side (see ConfigRequestHandler#handleSentienceUpdate) against
 * hasPermissions(4) exactly like every other Server-tab edit - never trusted just because the
 * Sentience Editor is hidden client-side for non-admins.
 */
public record SetSentienceOverridePayload(
        String entityId,
        boolean hasTier,
        String tierName,
        boolean hasReactionPower,
        int reactionPower
) implements CustomPacketPayload {

    public static final Type<SetSentienceOverridePayload> TYPE = new Type<>(DragonSpeech.id("set_sentience_override"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetSentienceOverridePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(256), SetSentienceOverridePayload::entityId,
            ByteBufCodecs.BOOL, SetSentienceOverridePayload::hasTier,
            ByteBufCodecs.stringUtf8(64), SetSentienceOverridePayload::tierName,
            ByteBufCodecs.BOOL, SetSentienceOverridePayload::hasReactionPower,
            ByteBufCodecs.INT, SetSentienceOverridePayload::reactionPower,
            SetSentienceOverridePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
