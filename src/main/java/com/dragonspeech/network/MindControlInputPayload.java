package com.dragonspeech.network;

import com.dragonspeech.DragonSpeech;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client -> server: the attacker's live controls while their camera is attached to a controlled mind. */
public record MindControlInputPayload(int flags, float yaw, float pitch) implements CustomPacketPayload {

    public static final int FORWARD = 1;
    public static final int BACK = 1 << 1;
    public static final int LEFT = 1 << 2;
    public static final int RIGHT = 1 << 3;
    public static final int JUMP = 1 << 4;
    public static final int SNEAK = 1 << 5;
    public static final int ATTACK = 1 << 6;
    public static final int USE = 1 << 7;

    public static final Type<MindControlInputPayload> TYPE = new Type<>(DragonSpeech.id("mind_control_input"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MindControlInputPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, MindControlInputPayload::flags,
        ByteBufCodecs.FLOAT, MindControlInputPayload::yaw,
        ByteBufCodecs.FLOAT, MindControlInputPayload::pitch,
        MindControlInputPayload::new
    );

    public boolean has(int flag) {
        return (flags & flag) != 0;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
