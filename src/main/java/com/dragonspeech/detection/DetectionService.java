package com.dragonspeech.detection;

import com.dragonspeech.engine.EntityMark;
import com.dragonspeech.engine.MarkRegistry;
import com.dragonspeech.stamina.MobStaminaAccess;
import com.dragonspeech.stamina.MobStaminaScaling;
import com.dragonspeech.stamina.PlayerMagicData;
import com.dragonspeech.stamina.StaminaAccess;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Backs "skynja afl"/"skynja naerum afl" (stamina) and "skynja marka"/
 * "skynja naerum marka" (marks) per explicit direction: a temporary
 * reveal shown above the target's head, ~5 seconds by default, longer
 * with a magnitude modifier (mikla, etc).
 *
 * Reuses the REAL stamina/mark storage this project already has rather
 * than inventing a parallel one: StaminaAccess/PlayerMagicData for
 * players, MobStaminaAccess/MobStaminaReserve (a generic notional pool
 * for any other LivingEntity - vanilla mobs included) for everyone else,
 * and MarkRegistry (already generic over any Entity) for marks. This
 * means detection sees the exact same numbers DrainStaminaEffectHandler
 * and MarkEffectHandler already work with, not a separate mob-only
 * concept.
 *
 * The "bar above the head" is the target's actual nametag, temporarily
 * overridden with a colored text bar and restored afterward - not a new
 * custom HUD/world-space renderer. This is a deliberate simplification:
 * it needs zero new client-side rendering code (nametags already render
 * as a billboard above any entity automatically), can be colored to
 * match StaminaHudOverlay's own gold tone, and "reads above their head"
 * exactly as asked. A literal graphical bar widget would need a
 * dedicated world-space HUD renderer - possible as later, separate work,
 * but meaningfully higher-risk than this.
 *
 * Currently wired up ONLY for the mob-casting system (Elf/Elder Elf/
 * Human Mage/Shade casting this at their target - see
 * MobDetectionCastGoal). Making "skynja afl/marka" work for a PLAYER
 * casting on another entity would additionally require changing the
 * real SkynjaEffectHandler (currently selfTargeting() = true, and
 * self-check is a different, already-useful behavior worth keeping) -
 * deliberately not touched here to avoid risking that working system
 * inside this same pass. Happy to build that as a focused follow-up.
 */
public final class DetectionService {

    private DetectionService() {}

    private static final int BASE_DURATION_TICKS = 100; // 5 seconds
    private static final int BAR_SEGMENTS = 10;
    private static final int PARTICLE_INTERVAL_TICKS = 10;

    private enum Kind { STAMINA, MARK }

    private static final class Active {
        final Kind kind;
        final Component originalName;
        final boolean originalNameVisible;
        long expireGameTime;
        long lastRefresh;

        Active(Kind kind, Component originalName, boolean originalNameVisible, long expireGameTime, long lastRefresh) {
            this.kind = kind;
            this.originalName = originalName;
            this.originalNameVisible = originalNameVisible;
            this.expireGameTime = expireGameTime;
            this.lastRefresh = lastRefresh;
        }
    }

    private static final Map<UUID, Active> ACTIVE = new HashMap<>();

    /** magnitudeBonus is composition.modifierMagnitudeSum() (or 0) - a positive modifier (mikla, etc) extends the duration proportionally, per direction ("unless they use mikla or some other modifier"). */
    public static void revealStamina(ServerLevel level, LivingEntity target, float magnitudeBonus) {
        int duration = BASE_DURATION_TICKS + Math.round(BASE_DURATION_TICKS * Math.max(0f, magnitudeBonus));
        long now = level.getGameTime();
        Active existing = ACTIVE.get(target.getUUID());
        Component originalName = existing != null ? existing.originalName : target.getCustomName();
        boolean originalVisible = existing != null ? existing.originalNameVisible : target.isCustomNameVisible();
        ACTIVE.put(target.getUUID(), new Active(Kind.STAMINA, originalName, originalVisible, now + duration, now));
        applyStaminaName(target, now);
    }

    public static void revealMark(ServerLevel level, LivingEntity target, float magnitudeBonus) {
        int duration = BASE_DURATION_TICKS + Math.round(BASE_DURATION_TICKS * Math.max(0f, magnitudeBonus));
        long now = level.getGameTime();
        Active existing = ACTIVE.get(target.getUUID());
        Component originalName = existing != null ? existing.originalName : target.getCustomName();
        boolean originalVisible = existing != null ? existing.originalNameVisible : target.isCustomNameVisible();
        ACTIVE.put(target.getUUID(), new Active(Kind.MARK, originalName, originalVisible, now + duration, now - PARTICLE_INTERVAL_TICKS));
        spawnMarkParticles(level, target);
    }

    /** Call once per server tick per loaded level - see DragonSpeech.java's tick registration. */
    public static void tick(ServerLevel level) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        Iterator<Map.Entry<UUID, Active>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Active> entry = it.next();
            Entity entity = level.getEntity(entry.getKey());
            Active active = entry.getValue();

            if (!(entity instanceof LivingEntity living) || !living.isAlive() || now >= active.expireGameTime) {
                if (entity instanceof LivingEntity living2) {
                    restore(living2, active);
                }
                it.remove();
                continue;
            }

            if (active.kind == Kind.STAMINA) {
                applyStaminaName(living, now);
            } else if (now - active.lastRefresh >= PARTICLE_INTERVAL_TICKS) {
                spawnMarkParticles(level, living);
                active.lastRefresh = now;
            }
        }
    }

    private static void applyStaminaName(LivingEntity target, long nowGameTime) {
        float current;
        float max;
        if (target instanceof ServerPlayer player) {
            PlayerMagicData data = StaminaAccess.get(player);
            current = data.stamina();
            max = data.maxStamina();
        } else {
            current = MobStaminaAccess.get(target, nowGameTime);
            max = MobStaminaScaling.maxFor(target);
        }

        float fraction = max > 0f ? Math.min(1f, current / max) : 0f;
        int filled = Math.round(BAR_SEGMENTS * fraction);
        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < BAR_SEGMENTS; i++) {
            bar.append(i < filled ? '\u2588' : '\u2591');
        }
        // GOLD is the closest named vanilla ChatFormatting to
        // StaminaHudOverlay's own 0xFFE8C030 - nametags don't support
        // arbitrary hex colors through Component styling the way the HUD
        // bar's raw fill color does.
        Component name = Component.literal(bar + " " + Math.round(current) + "/" + Math.round(max))
            .withStyle(ChatFormatting.GOLD);
        target.setCustomName(name);
        target.setCustomNameVisible(true);
    }

    private static void spawnMarkParticles(ServerLevel level, LivingEntity target) {
        Optional<EntityMark> mark = MarkRegistry.get(target);
        if (mark.isEmpty()) {
            return; // nothing to show - silently does nothing rather than announcing an absence with no player to tell
        }
        Vector3f color = mark.get() == EntityMark.GOOD ? new Vector3f(1f, 1f, 1f) : new Vector3f(0.05f, 0.05f, 0.05f);
        DustParticleOptions options = new DustParticleOptions(color, 1.2f);
        level.sendParticles(options, target.getX(), target.getEyeY() + 0.5, target.getZ(), 10, 0.25, 0.25, 0.25, 0.0);
    }

    private static void restore(LivingEntity target, Active active) {
        target.setCustomName(active.originalName);
        target.setCustomNameVisible(active.originalNameVisible);
    }
}
