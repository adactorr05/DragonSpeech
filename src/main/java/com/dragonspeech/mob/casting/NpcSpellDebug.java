package com.dragonspeech.mob.casting;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Backs "/dragonspeech debug npcspells <true|false>" - shows every
 * enabled admin what a mob just cast the instant it casts it: which
 * words made up the sentence and at whom, e.g. "[NPC Spell] shade ->
 * Steve: "eldingkast ofsa thetta" (shock)". Called from
 * MobCastExecutor.tryCast on every successful cast; a no-op (cheap
 * ENABLED.isEmpty() check) when nobody has it on.
 */
public final class NpcSpellDebug {

    private NpcSpellDebug() {}

    private static final Set<UUID> ENABLED = new HashSet<>();

    public static void set(ServerPlayer player, boolean enabled) {
        if (enabled) {
            ENABLED.add(player.getUUID());
        } else {
            ENABLED.remove(player.getUUID());
        }
    }

    public static void announce(SpellcastingMob caster, com.dragonspeech.spell.SpellComposition composition, LivingEntity target) {
        if (ENABLED.isEmpty()) {
            return;
        }
        LivingEntity self = caster.asEntity();
        if (!(self.level() instanceof ServerLevel level)) {
            return;
        }
        String sentence = composition.words().stream()
            .map(com.dragonspeech.word.Word::trueName)
            .collect(Collectors.joining(" "));
        Component message = Component.literal("[NPC Spell] " + self.getType().toShortString()
            + " -> " + target.getName().getString() + ": \"" + sentence + "\"");

        for (Player player : level.players()) {
            if (player instanceof ServerPlayer serverPlayer && ENABLED.contains(serverPlayer.getUUID())) {
                serverPlayer.sendSystemMessage(message);
            }
        }
    }
}
