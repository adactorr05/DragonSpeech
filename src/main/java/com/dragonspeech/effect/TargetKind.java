package com.dragonspeech.effect;

/**
 * The kinds of thing an EffectHandler is allowed to touch. Every handler
 * declares which of these it accepts via EffectHandlerCaps - this is one
 * of the hard boundaries that keeps "infinite spells" from ever meaning
 * "unrestricted access to arbitrary game state."
 *
 * DIRECTION exists for the "marklaust" (targetless) grammar: a handler
 * that opts into it agrees to resolve its own strike point along the
 * given direction (usually via Strikes.ray), instead of being handed a
 * pre-resolved block/entity.
 */
public enum TargetKind {
    BLOCK,
    ENTITY,
    DIRECTION;

    public static TargetKind of(EffectTarget target) {
        return switch (target) {
            case EffectTarget.OfBlock ignored -> BLOCK;
            case EffectTarget.OfEntity ignored -> ENTITY;
            case EffectTarget.OfDirection ignored -> DIRECTION;
        };
    }
}
