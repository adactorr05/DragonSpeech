package com.dragonspeech.mob.casting;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Backs "/dragonspeech showwards <true|false>" (also reachable as
 * "/dragonspeech debug showwards <true|false>" - see DebugCommand) -
 * default false/off. While on, shows the wards of whatever the player is
 * currently looking at, as an action-bar message refreshed every tick.
 *
 * Shows BOTH ward systems now - a real bug, not a display choice: this
 * only ever read Warded.activeWards() (a mob's own innate MobWards, from
 * MobWards.rollStartingWards at spawn), never WardAccess (the lightweight
 * "verja"-family wards ANY entity can now be given by a player - see
 * that class's own doc on being widened from ServerPlayer). "If I place
 * a ward on them myself, the ward works, but it doesn't show up on their
 * wards list" per explicit direction - that's exactly this gap; a player
 * -granted ward was always being applied and absorbed correctly, this
 * command just never looked in the right place to report it.
 *
 * Also widened the raycast filter itself from Warded-only to any
 * LivingEntity, for the same reason - literally anything can carry a
 * WardAccess-based ward now, not just the two mob races with innate
 * MobWards.
 */
public final class WardVisibility {
    private WardVisibility() {}

    private static final double REACH = 12.0;

    private static final Set<UUID> ENABLED = new HashSet<>();

    public static void set(ServerPlayer player, boolean enabled) {
        if (enabled) {
            ENABLED.add(player.getUUID());
        } else {
            ENABLED.remove(player.getUUID());
        }
    }

    public static boolean isEnabled(ServerPlayer player) {
        return ENABLED.contains(player.getUUID());
    }

    /** Call once per server tick for every online player - see DragonSpeech.java's tick registration. */
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
            candidate -> candidate instanceof LivingEntity,
            REACH * REACH
        );
        if (hit == null || !(hit.getEntity() instanceof LivingEntity target)) {
            return;
        }

        List<String> parts = new ArrayList<>();
        if (target instanceof Warded warded && !warded.activeWards().isEmpty()) {
            warded.activeWards().values().forEach(w ->
                parts.add(w.type().name() + " " + Math.round(w.durability()) + "/" + Math.round(w.maxDurability())));
        }
        for (var granted : com.dragonspeech.ward.WardAccess.get(target).wards()) {
            if (granted.isBroken()) {
                continue;
            }
            float remaining = granted.remainingEnergy();
            float max = granted.maxEnergy();
            if (granted.staminaBound() && target instanceof ServerPlayer targetPlayer) {
                var data = com.dragonspeech.stamina.StaminaAccess.get(targetPlayer);
                remaining = data.stamina();
                max = data.maxStamina();
            }
            String label = granted.type().getSerializedName().toUpperCase(java.util.Locale.ROOT)
                + " " + Math.round(remaining) + "/" + Math.round(max)
                + (granted.staminaBound() ? " (stamina-bound)" : "");
            parts.add(label);
        }

        String text = parts.isEmpty() ? "no active wards" : String.join(", ", parts);
        player.displayClientMessage(Component.literal(hit.getEntity().getName().getString() + " wards: " + text), true);
    }
}
