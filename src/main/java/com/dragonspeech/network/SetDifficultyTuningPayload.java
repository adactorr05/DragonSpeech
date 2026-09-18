package com.dragonspeech.network;

import com.dragonspeech.DragonSpeech;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client -> server: "here's the whole tuning ruleset for ONE difficulty tier" - the config GUI's
 * DifficultyTuningScreen. Scoped to a single tier per packet (same reasoning as
 * SetSentienceOverridePayload's own doc: this screen edits 3 tiers x 4 values, and there's no reason
 * changing one slider on EASY should also resend NORMAL/HARD's values).
 *
 * Re-validated server-side against hasPermissions(4) exactly like every other Server-tab edit - never
 * trusted just because DifficultyTuningScreen is hidden client-side for non-admins.
 */
public record SetDifficultyTuningPayload(
        String tierName,
        float healthFloor,
        float regenMultiplier,
        float costMultiplier,
        float structureSpacing
) implements CustomPacketPayload {

    public static final Type<SetDifficultyTuningPayload> TYPE = new Type<>(DragonSpeech.id("set_difficulty_tuning"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetDifficultyTuningPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(16), SetDifficultyTuningPayload::tierName,
            ByteBufCodecs.FLOAT, SetDifficultyTuningPayload::healthFloor,
            ByteBufCodecs.FLOAT, SetDifficultyTuningPayload::regenMultiplier,
            ByteBufCodecs.FLOAT, SetDifficultyTuningPayload::costMultiplier,
            ByteBufCodecs.FLOAT, SetDifficultyTuningPayload::structureSpacing,
            SetDifficultyTuningPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
