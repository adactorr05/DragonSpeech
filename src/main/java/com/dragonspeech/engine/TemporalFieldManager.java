package com.dragonspeech.engine;

import com.dragonspeech.fx.DragonSpeechParticles;
import com.dragonspeech.fx.SpellFx;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * The lingering half of the time-binding (kringla): a bubble that follows
 * the caster, continuously affecting anyone else who enters it for as
 * long as the field lasts - the caster themself is never touched by their
 * own field, by design ("I want to be outside of the flow of time while
 * everything else in the area is under its effect"). This is the genuine
 * area-effect counterpart to TemporalWorkingHandler's default instant
 * snapshot (which only affects whoever happened to be in range at the
 * moment of casting, and never re-checks afterward).
 *
 * Same in-memory, dissipates-on-restart model as SigilManager and
 * StasisManager - a held working never persists past a server restart, so
 * a crash can never leave a permanent time-distortion field behind.
 *
 * Re-scans every REFRESH_INTERVAL ticks rather than every single tick -
 * cheap, and the reapplied MobEffectInstance duration comfortably
 * outlasts the gap so nothing flickers off between scans.
 */
public final class TemporalFieldManager {

    private static final int REFRESH_INTERVAL_TICKS = 15;
    private static final int EFFECT_REFRESH_DURATION_TICKS = 32;

    private record Field(ResourceKey<Level> dimension, UUID casterId, double radius,
                         float tempo, int amplifier, boolean stasis, long expiresAtTick) {}

    private static final List<Field> ACTIVE = new ArrayList<>();

    private TemporalFieldManager() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(TemporalFieldManager::tick);
    }

    public static void place(ServerLevel level, ServerPlayer caster, double radius,
                             float tempo, int amplifier, boolean stasis, int durationTicks) {
        ACTIVE.add(new Field(level.dimension(), caster.getUUID(), radius, tempo, amplifier, stasis,
            level.getServer().getTickCount() + durationTicks));
    }

    private static void tick(MinecraftServer server) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        long now = server.getTickCount();

        Iterator<Field> iterator = ACTIVE.iterator();
        while (iterator.hasNext()) {
            Field field = iterator.next();

            ServerPlayer caster = server.getPlayerList().getPlayer(field.casterId());
            ServerLevel level = caster != null ? (ServerLevel) caster.level() : null;

            if (caster == null || level == null || !level.dimension().equals(field.dimension()) || now >= field.expiresAtTick()) {
                iterator.remove();
                continue;
            }

            Vec3 center = caster.position();

            if (now % REFRESH_INTERVAL_TICKS == 0) {
                affectArea(level, caster, center, field);
                idleFx(level, center, field);
            }
        }
    }

    private static void affectArea(ServerLevel level, ServerPlayer caster, Vec3 center, Field field) {
        boolean slowing = field.stasis() || field.tempo() < 0;

        for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class,
            new net.minecraft.world.phys.AABB(center, center).inflate(field.radius()),
            e -> e != caster && e.isAlive() && !e.isSpectator())) {

            if (field.stasis()) {
                if (living instanceof Mob mob) {
                    StasisManager.hold(level, mob, EFFECT_REFRESH_DURATION_TICKS + REFRESH_INTERVAL_TICKS);
                } else {
                    living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, EFFECT_REFRESH_DURATION_TICKS, 6));
                    living.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, EFFECT_REFRESH_DURATION_TICKS, 3));
                }
            } else if (field.tempo() < 0) {
                living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, EFFECT_REFRESH_DURATION_TICKS, field.amplifier()));
                living.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, EFFECT_REFRESH_DURATION_TICKS, Math.max(0, field.amplifier() - 1)));
            } else {
                living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, EFFECT_REFRESH_DURATION_TICKS, field.amplifier()));
                living.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, EFFECT_REFRESH_DURATION_TICKS, field.amplifier()));
            }

            // "it slows other things too - explosions, fire, damage" -
            // anyone standing in a slow/stasis field right now is marked
            // for TimeSlowDamageHooks to dampen explosion/general damage
            // against, and any fire already on them is stretched out to
            // match. See TimeSlowRegistry for why this is shared with
            // the instant single-target working too.
            if (slowing) {
                TimeSlowRegistry.mark(living.getUUID(), level.getGameTime(), EFFECT_REFRESH_DURATION_TICKS + REFRESH_INTERVAL_TICKS);
                TimeSlowRegistry.stretchFireIfBurning(living, REFRESH_INTERVAL_TICKS);
            }
        }

        if (slowing) {
            suppressFireSpread(level, center, field.radius());
        }
    }

    /**
     * The "fire" part that isn't about an entity already burning: fire
     * BLOCKS spreading from tile to tile within the field. Doesn't touch
     * FireBlock's own tick/spread logic (that would need a mixin into
     * genuinely core, extremely hot vanilla code - real risk for a
     * feature this scoped) - instead, each refresh, a fraction of the
     * fire blocks currently inside the field are simply snuffed out
     * before they get the chance to spread further. From the outside,
     * fire that wanders into a slowed field visibly struggles to take
     * hold, without this mod ever touching how fire itself works.
     */
    private static void suppressFireSpread(ServerLevel level, Vec3 center, double radius) {
        int scanRadius = (int) Math.min(radius, 16);
        net.minecraft.core.BlockPos centerPos = net.minecraft.core.BlockPos.containing(center);
        net.minecraft.core.BlockPos.betweenClosedStream(
                centerPos.offset(-scanRadius, -scanRadius, -scanRadius),
                centerPos.offset(scanRadius, scanRadius, scanRadius))
            .filter(pos -> pos.distSqr(centerPos) <= radius * radius)
            .filter(pos -> level.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.FIRE))
            .filter(pos -> level.random.nextFloat() < 0.35f) // thin it, don't instantly snuff the whole field every cycle
            .forEach(pos -> level.removeBlock(pos, false));
    }

    /** A thin ring at the field's edge, around the caster's feet, so the bubble's boundary is genuinely visible. */
    private static void idleFx(ServerLevel level, Vec3 center, Field field) {
        int color = field.stasis() ? 0xd8ccff : (field.tempo() < 0 ? 0x6a5acd : 0xfff4b8);
        int points = Math.max(10, (int) (field.radius() * 4));

        for (int i = 0; i < points; i++) {
            double angle = (Math.PI * 2 * i) / points;
            Vec3 pos = center.add(Math.cos(angle) * field.radius(), 0.1, Math.sin(angle) * field.radius());
            SpellFx.of(DragonSpeechParticles.SPARKLE)
                .pos(pos).color(color).time(REFRESH_INTERVAL_TICKS + 4).scale(0.5f)
                .spawn(level);
        }
    }
}
