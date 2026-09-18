package com.dragonspeech.network;

import com.dragonspeech.DragonSpeech;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client description of one composed spell body. Gameplay remains server-authoritative;
 * this packet contains only enough information to draw the sentence's resolved form and elements.
 */
public record SpellBodyVfxPayload(
    int effectType,
    long effectId,
    int ownerEntityId,
    int elementMask,
    double ax, double ay, double az,
    double bx, double by, double bz,
    float primary,
    float secondary,
    int lifetime,
    long seed
) implements CustomPacketPayload {

    public static final Type<SpellBodyVfxPayload> TYPE = new Type<>(DragonSpeech.id("spell_body_vfx"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SpellBodyVfxPayload> STREAM_CODEC = StreamCodec.of(
        (buf, p) -> {
            buf.writeVarInt(p.effectType());
            buf.writeLong(p.effectId());
            buf.writeVarInt(p.ownerEntityId());
            buf.writeVarInt(p.elementMask());
            buf.writeDouble(p.ax()); buf.writeDouble(p.ay()); buf.writeDouble(p.az());
            buf.writeDouble(p.bx()); buf.writeDouble(p.by()); buf.writeDouble(p.bz());
            buf.writeFloat(p.primary());
            buf.writeFloat(p.secondary());
            buf.writeVarInt(p.lifetime());
            buf.writeLong(p.seed());
        },
        buf -> new SpellBodyVfxPayload(
            buf.readVarInt(), buf.readLong(), buf.readVarInt(), buf.readVarInt(),
            buf.readDouble(), buf.readDouble(), buf.readDouble(),
            buf.readDouble(), buf.readDouble(), buf.readDouble(),
            buf.readFloat(), buf.readFloat(), buf.readVarInt(), buf.readLong()
        )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
