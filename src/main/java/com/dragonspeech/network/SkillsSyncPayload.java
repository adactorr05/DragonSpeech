package com.dragonspeech.network;

import com.dragonspeech.DragonSpeech;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server -> client: the receiving player's OWN learned skills (PlayerSkills), for the Grimoire's skills panel. Same "JSON string over the wire" shape as WardSyncPayload/ScarSyncPayload, for the same reason - one flexible payload type instead of a bespoke one per boolean. */
public record SkillsSyncPayload(String skillsJson) implements CustomPacketPayload {

    public static final Type<SkillsSyncPayload> TYPE = new Type<>(DragonSpeech.id("skills_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SkillsSyncPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, SkillsSyncPayload::skillsJson,
        SkillsSyncPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
