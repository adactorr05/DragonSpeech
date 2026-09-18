package com.dragonspeech.effect;

import com.dragonspeech.spell.SpellComposition;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * A fully-resolved cast: who's casting, what words/grammar they used, and
 * what real targets (blocks/entities) the scope resolved to. This is built
 * by whatever resolves the casting grid or a guessed/typed spell into real
 * game objects - by the time an EffectHandler sees this, "what am I acting
 * on" is already answered; the handler only decides "how do I act on it,
 * within my caps."
 */
public record EffectInvocation(
    ServerPlayer caster,
    SpellComposition composition,
    List<EffectTarget> targets
) {
    public float modifierMagnitudeSum() {
        return composition.modifierMagnitudeSum();
    }
}
