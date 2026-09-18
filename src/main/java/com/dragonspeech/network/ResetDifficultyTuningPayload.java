package com.dragonspeech.network;

import com.dragonspeech.DragonSpeech;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client -> server: "Reset to Defaults" button on DifficultyTuningScreen - restores all 12
 * difficulty-tuning numbers (health floor/regen/cost/structure spacing x 3 tiers) to their original
 * shipped values. Re-validated server-side against hasPermissions(4), same as every other Server-tab
 * edit.
 */
public record ResetDifficultyTuningPayload() implements CustomPacketPayload {

    public static final Type<ResetDifficultyTuningPayload> TYPE = new Type<>(DragonSpeech.id("reset_difficulty_tuning"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ResetDifficultyTuningPayload> STREAM_CODEC =
        StreamCodec.unit(new ResetDifficultyTuningPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
