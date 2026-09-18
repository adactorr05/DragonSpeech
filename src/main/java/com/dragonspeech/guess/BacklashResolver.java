package com.dragonspeech.guess;

import com.dragonspeech.stamina.DrainResolver;
import com.dragonspeech.word.RiskTier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * Applies the consequence of a wrong (or near-miss) guess, scaled by the
 * word's RiskTier. Deliberately reuses the SAME DrainResolver every other
 * cost in the mod goes through - a bad guess drains stamina, then hunger,
 * then health, exactly like an overreaching spell does. Guessing wrong
 * IS a kind of overreach, mechanically.
 *
 * Every number here is a balance knob - tune freely as you playtest.
 */
public final class BacklashResolver {

    private BacklashResolver() {}

    public static void applyBacklash(ServerPlayer player, RiskTier tier, float severityScale) {
        float baseCost = switch (tier) {
            case TRIVIAL -> 2f;
            case MODERATE -> 8f;
            case SEVERE -> 20f;
            case CATASTROPHIC -> 45f;
        };

        // Config GUI (Server tab) "Backlash Severity Multiplier" - an admin-wide knob on top of the word's own severityScale.
        DrainResolver.applyDrain(player, baseCost * severityScale * com.dragonspeech.config.DragonSpeechConfig.backlashSeverityMultiplier());
        applyTierEffects(player, tier, severityScale);

        BacklashAuditLog.record(player.level().getGameTime(),
            player.getGameProfile().getName(), tier, severityScale);
    }

    private static void applyTierEffects(ServerPlayer player, RiskTier tier, float scale) {
        switch (tier) {
            case TRIVIAL -> player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, scaledTicks(60, scale), 0));
            case MODERATE -> player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, scaledTicks(100, scale), 1));
            case SEVERE -> {
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, scaledTicks(140, scale), 2));
                player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, scaledTicks(140, scale), 1));
            }
            case CATASTROPHIC -> {
                player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, scaledTicks(200, scale), 2));
                player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, scaledTicks(100, scale), 0));
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, scaledTicks(200, scale), 3));
            }
        }
    }

    private static int scaledTicks(int baseTicks, float scale) {
        return Math.max(20, Math.round(baseTicks * scale));
    }
}
