package com.dragonspeech.mob.casting;

import com.dragonspeech.human.HumanMageEntity;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Backs "/dragonspeech debug audit <true|false>" - a full diagnostic
 * dump of whatever SpellcastingMob/Warded entity you right-click, sent
 * as a multi-line chat message (an action-bar line is too small for
 * this much information, unlike the other debug toggles). Always
 * returns InteractionResult.PASS so it never blocks the entity's normal
 * interaction (trading, etc.) - it just also prints, in addition to
 * whatever else happens.
 */
public final class EntityAudit {
    private EntityAudit() {}

    private static final Set<UUID> ENABLED = new HashSet<>();

    public static void set(net.minecraft.server.level.ServerPlayer player, boolean enabled) {
        if (enabled) {
            ENABLED.add(player.getUUID());
        } else {
            ENABLED.remove(player.getUUID());
        }
    }

    public static void register() {
        UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
            if (level.isClientSide()
                || !(player instanceof net.minecraft.server.level.ServerPlayer admin)
                || !ENABLED.contains(admin.getUUID())
                || !(entity instanceof LivingEntity target)) {
                return InteractionResult.PASS;
            }

            StringBuilder report = new StringBuilder();
            report.append("=== Audit: ").append(target.getName().getString())
                .append(" (").append(target.getType().toShortString()).append(") ===\n");

            if (target instanceof SpellcastingMob caster) {
                report.append("Tier: ").append(caster.powerTier()).append("\n");
                report.append("Energy: ").append(Math.round(caster.mysticalEnergy()))
                    .append("/").append(Math.round(caster.maxMysticalEnergy())).append("\n");
                report.append("Cast cooldown ready: ").append(caster.canCastNow()).append("\n");
                var words = caster.vocabulary().words();
                String sample = words.stream()
                    .map(id -> {
                        var word = com.dragonspeech.word.WordRegistry.get(id);
                        return word != null ? word.trueName() : id.toString();
                    })
                    .limit(12)
                    .collect(Collectors.joining(", "));
                report.append("Vocabulary (").append(words.size()).append(" total): ").append(sample);
                if (words.size() > 12) {
                    report.append(", ...");
                }
                report.append("\n");
            } else {
                report.append("Not a SpellcastingMob.\n");
            }

            if (target instanceof Warded warded) {
                var wards = warded.activeWards();
                String wardText = wards.isEmpty() ? "none" : wards.values().stream()
                    .map(w -> w.type().name() + " " + Math.round(w.durability()) + "/" + Math.round(w.maxDurability()))
                    .collect(Collectors.joining(", "));
                report.append("Active innate (MobWards) wards: ").append(wardText).append("\n");
            }

            // Player-granted (WardAccess) wards - a SEPARATE pool from
            // the innate MobWards one above, and the one that was
            // missing entirely before: "if I place a ward on them
            // myself, the ward works, but it doesn't show up on their
            // wards list" per explicit direction.
            var grantedWards = com.dragonspeech.ward.WardAccess.get(target).wards().stream()
                .filter(w -> !w.isBroken())
                .toList();
            String grantedText = grantedWards.isEmpty() ? "none" : grantedWards.stream()
                .map(w -> {
                    float remaining = w.remainingEnergy();
                    float max = w.maxEnergy();
                    if (w.staminaBound() && target instanceof net.minecraft.server.level.ServerPlayer targetPlayer) {
                        var data = com.dragonspeech.stamina.StaminaAccess.get(targetPlayer);
                        remaining = data.stamina();
                        max = data.maxStamina();
                    }
                    return w.type().getSerializedName().toUpperCase(java.util.Locale.ROOT) + " "
                        + Math.round(remaining) + "/" + Math.round(max) + (w.staminaBound() ? " (stamina-bound)" : "");
                })
                .collect(Collectors.joining(", "));
            report.append("Player-granted (WardAccess) wards: ").append(grantedText).append("\n");

            if (target instanceof HumanMageEntity mage) {
                report.append("Hostile roll: ").append(mage.isHostile()).append("\n");
            }

            if (target instanceof net.minecraft.world.entity.Mob mob) {
                report.append("Persistence required: ").append(mob.isPersistenceRequired());
            }

            admin.sendSystemMessage(Component.literal(report.toString()));
            return InteractionResult.PASS;
        });
    }
}
