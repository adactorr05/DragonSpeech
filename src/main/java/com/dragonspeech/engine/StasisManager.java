package com.dragonspeech.engine;

import com.dragonspeech.fx.DragonSpeechParticles;
import com.dragonspeech.fx.SpellFx;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Holds kyrra'd mobs outside time: AI off, gravity off, velocity zeroed
 * and position re-pinned every tick, released with their prior NoAI /
 * NoGravity flags exactly restored (so a kyrra'd armor stand exhibit mob
 * or an already-NoAI map decoration isn't "fixed" by being released).
 *
 * Same in-memory, dissipates-on-restart model as SigilManager and the
 * channel system: a held working never persists, so a crash can never
 * leave a mob permanently frozen. Behavior reference: EBW's Arrest.
 *
 * The frozen mob still takes damage normally - a thing held outside time
 * cannot dodge, block, or retaliate, and that vulnerability IS the
 * spell's payoff, exactly like EBW's.
 */
public final class StasisManager {

    private record Stasis(ResourceKey<Level> dimension, UUID mobId, Vec3 heldPos,
                          float heldYRot, long expiresAtTick,
                          boolean hadNoAi, boolean hadNoGravity) {}

    private static final List<Stasis> ACTIVE = new ArrayList<>();
    private static final Set<UUID> HELD_IDS = new HashSet<>();

    private StasisManager() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(StasisManager::tick);
    }

    public static boolean isHeld(UUID mobId) {
        return HELD_IDS.contains(mobId);
    }

    public static void hold(ServerLevel level, Mob mob, int durationTicks) {
        if (HELD_IDS.contains(mob.getUUID())) {
            return; // already outside time; re-casting doesn't stack or extend
        }

        ACTIVE.add(new Stasis(level.dimension(), mob.getUUID(), mob.position(), mob.getYRot(),
            level.getServer().getTickCount() + durationTicks,
            mob.isNoAi(), mob.isNoGravity()));
        HELD_IDS.add(mob.getUUID());

        mob.setNoAi(true);
        mob.setNoGravity(true);
        mob.setDeltaMovement(Vec3.ZERO);
        mob.getNavigation().stop();
        mob.setTarget(null);
    }

    private static void tick(MinecraftServer server) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        long now = server.getTickCount();

        Iterator<Stasis> iterator = ACTIVE.iterator();
        while (iterator.hasNext()) {
            Stasis stasis = iterator.next();

            ServerLevel level = server.getLevel(stasis.dimension());
            Mob mob = level != null && level.getEntity(stasis.mobId()) instanceof Mob found ? found : null;

            if (mob == null || !mob.isAlive() || now >= stasis.expiresAtTick()) {
                if (mob != null && mob.isAlive()) {
                    release(mob, stasis);
                }
                HELD_IDS.remove(stasis.mobId());
                iterator.remove();
                continue;
            }

            // Re-pin every tick: knockback from hits, water push, pistons -
            // nothing moves a thing that time is not carrying.
            mob.setDeltaMovement(Vec3.ZERO);
            mob.moveTo(stasis.heldPos().x, stasis.heldPos().y, stasis.heldPos().z, stasis.heldYRot(), mob.getXRot());
            mob.fallDistance = 0;

            // A faint suspended shimmer, so the stillness reads as magic.
            if (now % 10 == 0) {
                Vec3 center = stasis.heldPos().add(0, mob.getBbHeight() * 0.5, 0);
                SpellFx.of(DragonSpeechParticles.SPARKLE)
                    .pos(center).color(0xd8ccff).fade(0xffffff)
                    .time(24).scale(0.6f)
                    .count(3).jitter(mob.getBbWidth() * 0.6)
                    .spawn(level);
            }
        }
    }

    private static void release(Mob mob, Stasis stasis) {
        mob.setNoAi(stasis.hadNoAi());
        mob.setNoGravity(stasis.hadNoGravity());
        if (mob.level() instanceof ServerLevel level) {
            Vec3 center = mob.position().add(0, mob.getBbHeight() * 0.5, 0);
            SpellFx.flash(level, 0xd8ccff, center);
        }
    }
}
