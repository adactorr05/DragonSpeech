package com.dragonspeech.effect;

import com.dragonspeech.DragonSpeech;
import com.dragonspeech.engine.EntityMark;
import com.dragonspeech.engine.MarkRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.Optional;
import java.util.Set;

/**
 * Backs "marka" - a lasting tag on a living target, naming it either
 * "blidr" (good/blessed) or "illr" (bad/cursed). Right now the only
 * consumer is MagicBarrierEntity's ward/cage exclusion check (a GOOD
 * mark passes through anything, like a player always can; a BAD mark is
 * always subject to it, even if the target IS a player, overriding that
 * usual exemption) - see MarkRegistry's own doc for the deliberate
 * "future systems" framing (scrying, curses/blessings), which this
 * class doesn't touch at all.
 *
 * Requires naming a nature ("blidr" or "illr") - "marka" alone doesn't
 * know what kind of mark to leave, so it refuses rather than guessing.
 * If somehow both are spoken, "illr" wins - a curse holding despite an
 * accompanying blessing-word reads as more consistent with what a curse
 * IS than the reverse would.
 *
 * "letta marka" - same "the control word alone/paired with a binding
 * releases what it names" idea wards and barriers already use ("letta
 * skjoldr" dispels a shield) - clears a mark from the target instead of
 * setting one. Doesn't need a nature word (blidr/illr); clearing a mark
 * is clearing it regardless of which kind it was.
 */
public class MarkEffectHandler implements EffectHandler {

    private static final EffectHandlerCaps CAPS = new EffectHandlerCaps(
        6, 4.0f, 24f, Set.of(TargetKind.ENTITY)
    );

    @Override
    public ResourceLocation id() {
        return DragonSpeech.id("mark");
    }

    @Override
    public EffectHandlerCaps caps() {
        return CAPS;
    }

    @Override
    public float estimateBaseMagnitude(EffectInvocation invocation) {
        if (isDispel(invocation)) {
            return 2f; // same "releasing is cheap" spirit as skjoldr's own dispel
        }
        return 3f * Math.max(invocation.targets().size(), 1);
    }

    @Override
    public EffectResult apply(EffectInvocation invocation) {
        Optional<String> capViolation = CAPS.validateTargets(invocation);
        if (capViolation.isPresent()) {
            return EffectResult.failure(capViolation.get());
        }

        if (isDispel(invocation)) {
            return applyDispel(invocation);
        }

        Optional<EntityMark> nature = resolveNature(invocation);
        if (nature.isEmpty()) {
            return EffectResult.failure(
                "The word reaches for a nature and finds none named - a marking must say what it leaves behind: blidr, or illr.");
        }

        int marked = 0;
        for (EffectTarget target : invocation.targets()) {
            if (target instanceof EffectTarget.OfEntity(Entity entity) && entity instanceof LivingEntity living) {
                MarkRegistry.set(living, nature.get());
                marked++;
            }
        }

        if (marked == 0) {
            return EffectResult.failure("There is nothing there to mark.");
        }

        String message = nature.get() == EntityMark.GOOD
            ? "A kind sign settles upon it, lasting and plain to see for those who know to look."
            : "A foul sign settles upon it, lasting and plain to see for those who know to look.";
        return EffectResult.success(marked, message);
    }

    private EffectResult applyDispel(EffectInvocation invocation) {
        int cleared = 0;
        for (EffectTarget target : invocation.targets()) {
            if (target instanceof EffectTarget.OfEntity(Entity entity) && entity instanceof LivingEntity living
                    && MarkRegistry.get(living).isPresent()) {
                MarkRegistry.clear(living);
                cleared++;
            }
        }
        if (cleared == 0) {
            return EffectResult.failure("There is no mark there to lift.");
        }
        return EffectResult.success(cleared, "The sign lifts, and fades from sight.");
    }

    private static boolean isDispel(EffectInvocation invocation) {
        return invocation.composition().words().stream().anyMatch(w -> "letta".equals(w.trueName()));
    }

    private static Optional<EntityMark> resolveNature(EffectInvocation invocation) {
        boolean bad = invocation.composition().words().stream().anyMatch(w -> "illr".equals(w.trueName()));
        if (bad) {
            return Optional.of(EntityMark.BAD);
        }
        boolean good = invocation.composition().words().stream().anyMatch(w -> "blidr".equals(w.trueName()));
        if (good) {
            return Optional.of(EntityMark.GOOD);
        }
        return Optional.empty();
    }
}
