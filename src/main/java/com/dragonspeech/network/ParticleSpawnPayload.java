package com.dragonspeech.network;

import com.dragonspeech.DragonSpeech;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client: spawn one Dragon Speech magic particle (or a jittered
 * batch of `count` identical ones) with the full parameter set the DsParticle
 * system supports - colour, fade colour, lifetime, scale, spin, entity
 * linking, a target point/entity for stretched particles (beams,
 * lightning arcs), fixed facing, and a shared random seed.
 *
 * This is the serialized form of SpellFx's builder, mirroring EBW's
 * ParticleBuilder.ParticleData. NaN doubles / NaN floats / -1 ids mean
 * "unset - keep the particle class's own default", exactly as in EBW.
 *
 * The field count is far past StreamCodec.composite's arity limit, so the
 * codec below is a plain manual read/write via StreamCodec.of - every
 * field is fixed-width, so ordering is the only thing that matters, and
 * read() is the exact mirror of write().
 */
public record ParticleSpawnPayload(
    String particleId,
    double x, double y, double z,
    double vx, double vy, double vz,
    float r, float g, float b,
    float fr, float fg, float fb,
    int lifetime,
    float scale,
    boolean gravity, boolean shaded, boolean collide,
    double spinRadius, double spinSpeed,
    float yaw, float pitch,
    long seed,
    double length,
    double tx, double ty, double tz,
    int entityId, int targetId,
    int count, double jitter
) implements CustomPacketPayload {

    public static final Type<ParticleSpawnPayload> TYPE = new Type<>(DragonSpeech.id("particle_spawn"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ParticleSpawnPayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeUtf(payload.particleId());
            buf.writeDouble(payload.x());
            buf.writeDouble(payload.y());
            buf.writeDouble(payload.z());
            buf.writeDouble(payload.vx());
            buf.writeDouble(payload.vy());
            buf.writeDouble(payload.vz());
            buf.writeFloat(payload.r());
            buf.writeFloat(payload.g());
            buf.writeFloat(payload.b());
            buf.writeFloat(payload.fr());
            buf.writeFloat(payload.fg());
            buf.writeFloat(payload.fb());
            buf.writeInt(payload.lifetime());
            buf.writeFloat(payload.scale());
            buf.writeBoolean(payload.gravity());
            buf.writeBoolean(payload.shaded());
            buf.writeBoolean(payload.collide());
            buf.writeDouble(payload.spinRadius());
            buf.writeDouble(payload.spinSpeed());
            buf.writeFloat(payload.yaw());
            buf.writeFloat(payload.pitch());
            buf.writeLong(payload.seed());
            buf.writeDouble(payload.length());
            buf.writeDouble(payload.tx());
            buf.writeDouble(payload.ty());
            buf.writeDouble(payload.tz());
            buf.writeInt(payload.entityId());
            buf.writeInt(payload.targetId());
            buf.writeInt(payload.count());
            buf.writeDouble(payload.jitter());
        },
        buf -> new ParticleSpawnPayload(
            buf.readUtf(),
            buf.readDouble(), buf.readDouble(), buf.readDouble(),
            buf.readDouble(), buf.readDouble(), buf.readDouble(),
            buf.readFloat(), buf.readFloat(), buf.readFloat(),
            buf.readFloat(), buf.readFloat(), buf.readFloat(),
            buf.readInt(),
            buf.readFloat(),
            buf.readBoolean(), buf.readBoolean(), buf.readBoolean(),
            buf.readDouble(), buf.readDouble(),
            buf.readFloat(), buf.readFloat(),
            buf.readLong(),
            buf.readDouble(),
            buf.readDouble(), buf.readDouble(), buf.readDouble(),
            buf.readInt(), buf.readInt(),
            buf.readInt(), buf.readDouble()
        )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
