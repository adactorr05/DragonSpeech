package com.dragonspeech.mob.casting;

import com.dragonspeech.stamina.MobStaminaAccess;
import com.dragonspeech.stamina.MobStaminaScaling;
import com.dragonspeech.stamina.PlayerMagicData;
import com.dragonspeech.stamina.StaminaAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Backs "/dragonspeech debug staminaview <true|false>" - an ADMIN-ONLY
 * view of whatever entity/player they're looking at, shown as an action
 * bar message (same reach+raycast pattern as WardVisibility, and same
 * privacy property for free: displayClientMessage(..., true) only ever
 * reaches the one player who called it - no separate "who can see this"
 * mechanism needed, unlike the old mob-cast detection spell's nametag
 * approach).
 *
 * Reads the same real StaminaAccess (players) / MobStaminaAccess (any
 * other LivingEntity) this project already uses elsewhere - not a
 * separate invented number.
 */
public final class StaminaView {
    private StaminaView() {}

    private static final double REACH = 12.0;
    private static final int BAR_SEGMENTS = 10;

    private static final Set<UUID> ENABLED = new HashSet<>();

    public static void set(ServerPlayer player, boolean enabled) {
        if (enabled) {
            ENABLED.add(player.getUUID());
        } else {
            ENABLED.remove(player.getUUID());
        }
    }

    public static void tick(ServerPlayer player) {
        if (!ENABLED.contains(player.getUUID())) {
            return;
        }

        Vec3 start = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0f);
        Vec3 end = start.add(look.scale(REACH));
        var searchBox = player.getBoundingBox().expandTowards(look.scale(REACH)).inflate(1.0);

        EntityHitResult hit = ProjectileUtil.getEntityHitResult(
            player, start, end, searchBox,
            candidate -> candidate instanceof LivingEntity && candidate != player,
            REACH * REACH
        );
        if (hit == null || !(hit.getEntity() instanceof LivingEntity target)) {
            return;
        }

        float current;
        float max;
        if (target instanceof ServerPlayer targetPlayer) {
            PlayerMagicData data = StaminaAccess.get(targetPlayer);
            current = data.stamina();
            max = data.maxStamina();
        } else {
            current = MobStaminaAccess.get(target, target.level().getGameTime());
            max = MobStaminaScaling.maxFor(target);
        }

        float fraction = max > 0f ? Math.min(1f, current / max) : 0f;
        int filled = Math.round(BAR_SEGMENTS * fraction);
        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < BAR_SEGMENTS; i++) {
            bar.append(i < filled ? '\u2588' : '\u2591');
        }

        player.displayClientMessage(Component.literal(
            target.getName().getString() + " stamina: " + bar + " " + Math.round(current) + "/" + Math.round(max)
        ), true);
    }
}
