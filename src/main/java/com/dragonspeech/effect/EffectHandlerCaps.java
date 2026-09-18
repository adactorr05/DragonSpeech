package com.dragonspeech.effect;

import java.util.Optional;
import java.util.Set;

/**
 * The hard limits a handler operates under, checked BEFORE apply() is ever
 * called. This is what makes "spells are basically infinite, limited only
 * by imagination" safe: the grammar/wording can combine words in endless
 * ways, but every combination still funnels through a handler whose caps
 * cannot be raised by clever phrasing, precision, or stamina spent. A
 * player who wants a bigger effect needs a bigger, separately-designed
 * handler (or the Word of Words admin-grant path) - never a wording trick.
 */
public record EffectHandlerCaps(
    int maxTargets,
    float maxMagnitudePerTarget,
    float maxRangeBlocks,
    Set<TargetKind> allowedTargetKinds
) {
    /** Range is checked separately by the resolver, which is the one place that has distance available. */
    public Optional<String> validateTargets(EffectInvocation invocation) {
        if (invocation.targets().size() > maxTargets) {
            return Optional.of("Too many targets: " + invocation.targets().size() + " (max " + maxTargets + ")");
        }
        for (EffectTarget target : invocation.targets()) {
            TargetKind kind = TargetKind.of(target);
            if (!allowedTargetKinds.contains(kind)) {
                return Optional.of("Target kind " + kind + " is not valid for this effect");
            }
        }
        return Optional.empty();
    }

    public Optional<String> validateRange(float distanceFromCaster) {
        if (distanceFromCaster > maxRangeBlocks) {
            return Optional.of("Target is too far away: " + distanceFromCaster + " blocks (max " + maxRangeBlocks + ")");
        }
        return Optional.empty();
    }
}
