package com.dragonspeech.mind;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.LivingEntity;

/**
 * A single compiled command effect - mirrors EffectHandlerRegistry's own
 * rule: this is a FIXED, compiled-code set. ISSUE_COMMAND can only ever
 * select one of these by id; nothing about a duel or a command's chosen
 * wording can invent new behavior here. See CommandEffectRegistry for
 * the registered set and MindDuelActionService.resolveCommandPhase for
 * where apply() actually gets called (only ever after a resistance roll
 * has already failed - a CommandEffect never needs to check "did they
 * resist," only "make this happen").
 */
public record CommandEffect(String id, String label, float baseDifficulty, Apply apply) {

    @FunctionalInterface
    public interface Apply {
        void run(MinecraftServer server, LivingEntity defender, LivingEntity attacker);
    }
}
