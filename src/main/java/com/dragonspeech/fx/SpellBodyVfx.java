package com.dragonspeech.fx;

import com.dragonspeech.engine.Element;
import com.dragonspeech.network.SpellBodyVfxPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Server-side sender for Dragon Speech's geometry-first spell visuals.
 * The visual form is generic and the element mask is compositional, so woven sentences do not
 * need a hard-coded named spell or a dedicated packet type.
 */
public final class SpellBodyVfx {
    private static final double VIEW_RANGE = 160.0;
    private static final AtomicLong NEXT_ID = new AtomicLong(1L);

    private SpellBodyVfx() {}

    public static long emit(ServerLevel level, ServerPlayer owner, SpellBodyVfxType type,
                            List<Element> elements, Vec3 a, Vec3 b,
                            float primary, float secondary, int lifetime) {
        long id = NEXT_ID.getAndIncrement();
        send(level, owner, type, id, elements, a, b, primary, secondary, lifetime, level.getRandom().nextLong());
        return id;
    }

    public static void send(ServerLevel level, ServerPlayer owner, SpellBodyVfxType type, long id,
                            List<Element> elements, Vec3 a, Vec3 b,
                            float primary, float secondary, int lifetime, long seed) {
        int mask = elementMask(elements);
        SpellBodyVfxPayload payload = new SpellBodyVfxPayload(
            type.id(), id, owner == null ? -1 : owner.getId(), mask,
            a.x, a.y, a.z, b.x, b.y, b.z,
            primary, secondary, Math.max(1, lifetime), seed
        );
        Vec3 focus = a.lerp(b, 0.5);
        for (ServerPlayer player : level.players()) {
            if (player.position().distanceToSqr(focus) <= VIEW_RANGE * VIEW_RANGE) {
                ServerPlayNetworking.send(player, payload);
            }
        }
    }

    public static void remove(ServerLevel level, long id, Vec3 focus) {
        SpellBodyVfxPayload payload = new SpellBodyVfxPayload(
            SpellBodyVfxType.REMOVE.id(), id, -1, 0,
            focus.x, focus.y, focus.z, focus.x, focus.y, focus.z,
            0f, 0f, 1, 0L
        );
        for (ServerPlayer player : level.players()) {
            if (player.position().distanceToSqr(focus) <= VIEW_RANGE * VIEW_RANGE) {
                ServerPlayNetworking.send(player, payload);
            }
        }
    }

    public static int elementMask(List<Element> elements) {
        int mask = 0;
        for (Element element : elements) {
            int bit = element.ordinal();
            if (bit < Integer.SIZE - 1) mask |= 1 << bit;
        }
        return mask;
    }
}
