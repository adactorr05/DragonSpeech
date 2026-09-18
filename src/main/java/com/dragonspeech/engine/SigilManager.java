package com.dragonspeech.engine;

import com.dragonspeech.fx.SpellFx;
import com.dragonspeech.fx.SpellBodyVfx;
import com.dragonspeech.fx.SpellBodyVfxType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Tracks every placed ristmark (sigil) and ticks it: idle glow fx every
 * second, detonation on the first living non-caster to step onto it,
 * expiry after a minute. Deliberately in-memory only - a sigil is a held
 * working, and like every other held working in this mod (channels,
 * pending contacts) it dissipates on server restart rather than being
 * persisted, which also means a crash can never leave orphaned traps.
 *
 * Behavior reference: EBW's TileEntity-based sigils, reimplemented as a
 * lightweight tick list so no block/block-entity registration is needed.
 */
public final class SigilManager {

    private static final int LIFETIME_TICKS = 20 * 60;
    private static final double TRIGGER_RADIUS = 1.4;
    private static final float DETONATE_POWER_SCALE = 1.4f;

    private record Sigil(ResourceKey<Level> dimension, Vec3 pos, List<Element> elements,
                         float power, UUID caster, long expiresAtTick, long visualId) {}

    private static final List<Sigil> ACTIVE = new ArrayList<>();

    private SigilManager() {}

    /** Read-only descriptor used by the Word-of-Words GUI. */
    public record SigilView(long id, Vec3 pos, List<Element> elements, float power, UUID caster) {}

    public static List<SigilView> nearby(ServerLevel level, Vec3 center, double radius) {
        double r2 = radius * radius;
        List<SigilView> out = new ArrayList<>();
        for (Sigil sigil : ACTIVE) {
            if (!sigil.dimension().equals(level.dimension())) continue;
            if (sigil.pos().distanceToSqr(center) > r2) continue;
            out.add(new SigilView(sigil.visualId(), sigil.pos(), sigil.elements(), sigil.power(), sigil.caster()));
        }
        out.sort(java.util.Comparator.comparingDouble(v -> v.pos().distanceToSqr(center)));
        return List.copyOf(out);
    }

    public static boolean remove(ServerLevel level, long visualId) {
        Iterator<Sigil> iterator = ACTIVE.iterator();
        while (iterator.hasNext()) {
            Sigil sigil = iterator.next();
            if (sigil.visualId() == visualId && sigil.dimension().equals(level.dimension())) {
                SpellBodyVfx.remove(level, sigil.visualId(), sigil.pos());
                iterator.remove();
                return true;
            }
        }
        return false;
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(SigilManager::tick);
    }

    public static void place(ServerLevel level, Vec3 pos, List<Element> elements, float power, ServerPlayer caster) {
        long visualId = SpellBodyVfx.emit(level, caster, SpellBodyVfxType.SIGIL, elements, pos, pos,
            .82f + power * .02f, 0f, LIFETIME_TICKS);
        ACTIVE.add(new Sigil(level.dimension(), pos, List.copyOf(elements), power, caster.getUUID(),
            level.getServer().getTickCount() + LIFETIME_TICKS, visualId));
        idleFx(level, pos, elements);
    }

    private static void tick(MinecraftServer server) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        long now = server.getTickCount();

        Iterator<Sigil> iterator = ACTIVE.iterator();
        while (iterator.hasNext()) {
            Sigil sigil = iterator.next();

            if (now >= sigil.expiresAtTick()) {
                ServerLevel expiredLevel = server.getLevel(sigil.dimension());
                if (expiredLevel != null) SpellBodyVfx.remove(expiredLevel, sigil.visualId(), sigil.pos());
                iterator.remove();
                continue;
            }

            ServerLevel level = server.getLevel(sigil.dimension());
            if (level == null) {
                iterator.remove();
                continue;
            }

            // Quiet custom-mote accents once a second. Resend the same persistent geometry ID as
            // well so a player who walked into view after placement still sees the actual sigil body.
            if (now % 20 == 0) {
                idleFx(level, sigil.pos(), sigil.elements());
                int remaining = (int)Math.max(1, sigil.expiresAtTick() - now);
                SpellBodyVfx.send(level, null, SpellBodyVfxType.SIGIL, sigil.visualId(), sigil.elements(),
                    sigil.pos(), sigil.pos(), .82f + sigil.power() * .02f, 0f, remaining, level.getRandom().nextLong());
            }

            LivingEntity victim = null;
            for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class,
                new AABB(sigil.pos(), sigil.pos()).inflate(TRIGGER_RADIUS),
                e -> e.isAlive() && !e.isSpectator() && !e.getUUID().equals(sigil.caster()))) {
                victim = candidate;
                break;
            }
            if (victim == null) {
                continue;
            }

            // Detonate - amplified, because the victim walked onto a prepared word.
            ServerPlayer caster = server.getPlayerList().getPlayer(sigil.caster());
            for (Element element : sigil.elements()) {
                if (caster != null) {
                    element.hitEntity(caster, victim, sigil.power() * DETONATE_POWER_SCALE);
                }
                element.impactFx(level, sigil.pos().add(0, 0.4, 0));
            }
            SpellFx.flash(level, sigil.elements().isEmpty() ? 0xffffff : sigil.elements().get(0).color(),
                sigil.pos().add(0, 0.4, 0));

            SpellBodyVfx.remove(level, sigil.visualId(), sigil.pos());
            iterator.remove();
        }
    }

    private static void idleFx(ServerLevel level, Vec3 pos, List<Element> elements) {
        for (Element element : elements) {
            // A flat ring of motes lying on the ground, facing upward.
            for (int i = 0; i < 6; i++) {
                double angle = (Math.PI * 2 * i) / 6 + level.getRandom().nextDouble() * 0.4;
                SpellFx.of(element.trailParticle())
                    .pos(pos.add(Math.cos(angle) * 0.8, 0.08, Math.sin(angle) * 0.8))
                    .color(element.color())
                    .fade(element.fadeColor())
                    .time(18)
                    .scale(0.5f)
                    .face(0, 90)
                    .spawn(level);
            }
        }
    }
}
