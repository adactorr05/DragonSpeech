package com.dragonspeech.engine;

import com.dragonspeech.DragonSpeech;
import com.dragonspeech.effect.EffectHandler;
import com.dragonspeech.effect.EffectHandlerCaps;
import com.dragonspeech.effect.EffectInvocation;
import com.dragonspeech.effect.EffectResult;
import com.dragonspeech.effect.EffectTarget;
import com.dragonspeech.effect.KineticDirections;
import com.dragonspeech.effect.TargetKind;
import com.dragonspeech.word.Word;
import com.dragonspeech.word.WordCategory;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The elemental working: ONE handler that every attack/field form verb
 * points at (kasta, geisla, kula, sprengja, hringr, skyja, umljomi,
 * ristmark, regnfalla - plus element-flavoured verbs like eldingkast).
 * A spell here is NOT chosen from a list; it is interpreted from the
 * sentence:
 *
 *   element(s)  <- the verb's own element and/or element nouns
 *                  (eldr, elding, is, eitr, ljos, myrkr, nar, afl,
 *                  steinn, jord, vatn, frae); samvefja weaves several
 *   form        <- the shape carried by any spoken form verb
 *   power       <- verb precision + mikla/litla/ofsa
 *   count       <- margfalt/tvefalt (repeat_count)
 *   targeting   <- leitbinda (homing) / kedjubinda (chain)
 *   direction   <- frama/uppa/nidra/aftana, else the gaze
 *   reach       <- scope words (naerum/umhverf/viddum)
 *   free cast   <- marklaust (no bound target needed)
 *
 * "eldingkast thetta" and "samvefja eldr ok elding geisla frama" both end
 * here - the first as LIGHTNING+BOLT at a bound mark, the second as
 * FIRE+LIGHTNING woven into a RAY along the gaze.
 *
 * The security boundary is unchanged: words can only SELECT from the
 * compiled Element/SpellShape/TargetingStyle sets and this one handler's
 * caps - however creative the sentence, it can never exceed them.
 */
public class ElementalWorkingHandler implements EffectHandler {

    /** Weaving is capped: three elements is already a catastrophic-risk sentence. */
    private static final int MAX_WOVEN_ELEMENTS = 3;
    private static final int MAX_COUNT = 5;

    private static final EffectHandlerCaps CAPS = new EffectHandlerCaps(
        4, 12.0f, 32f, Set.of(TargetKind.ENTITY, TargetKind.BLOCK, TargetKind.DIRECTION)
    );

    private static final Map<SpellShape, FormEngine> ENGINES = new EnumMap<>(SpellShape.class);

    static {
        ENGINES.put(SpellShape.BOLT, ProjectileEngines.BOLT);
        ENGINES.put(SpellShape.RAY, ProjectileEngines.RAY);
        ENGINES.put(SpellShape.ORB, ProjectileEngines.ORB);
        ENGINES.put(SpellShape.BURST, FieldEngines.BURST);
        ENGINES.put(SpellShape.RING, FieldEngines.RING);
        ENGINES.put(SpellShape.RAIN, FieldEngines.RAIN);
        ENGINES.put(SpellShape.CLOUD, FieldEngines.CLOUD);
        ENGINES.put(SpellShape.AURA, FieldEngines.AURA);
        ENGINES.put(SpellShape.SIGIL, FieldEngines.SIGIL);
        ENGINES.put(SpellShape.LANCE, AdvancedFormEngines.LANCE);
        ENGINES.put(SpellShape.TETHER, AdvancedFormEngines.TETHER);
        ENGINES.put(SpellShape.CLAW, AdvancedFormEngines.CLAW);
        ENGINES.put(SpellShape.SPIRAL, AdvancedFormEngines.SPIRAL);
        ENGINES.put(SpellShape.SHELL, AdvancedFormEngines.SHELL);
    }

    @Override
    public ResourceLocation id() {
        return DragonSpeech.id("elemental_working");
    }

    @Override
    public EffectHandlerCaps caps() {
        return CAPS;
    }

    @Override
    public float estimateBaseMagnitude(EffectInvocation invocation) {
        SpellShape shape = resolveShape(invocation);
        int elements = Math.max(1, resolveElements(invocation).size());
        int count = resolveCount(invocation);
        int anchors = Math.max(1, invocation.targets().size());

        float shapeCost = switch (shape) {
            case BOLT -> 4f;
            case ORB, SIGIL -> 5f;
            case RAY, RING, AURA -> 6f;
            case BURST, CLOUD -> 7f;
            case RAIN -> 9f;
            case LANCE -> 7f;
            case TETHER, CLAW -> 6.5f;
            case SPIRAL, SHELL -> 8f;
        };

        // A sharp rotating ring that is explicitly CAST is a mobile construct rather than
        // the ordinary stationary hringr field.  The language remains compositional:
        // kasta (cast) + hringr (ring) + sveira (revolve) + hvassa (sharpen).
        boolean sharpRotatingRing = isSharpRotatingRing(invocation);
        boolean cuttingRing = isCuttingRing(invocation);
        if (sharpRotatingRing) shapeCost = Math.max(shapeCost, cuttingRing ? 9.5f : 8.0f);

        float semanticComplexity = 1f;
        if (invocation.composition().occurrencesOf("samdraga") > 0) semanticComplexity *= 1.12f;
        if (invocation.composition().occurrencesOf("sveira") > 0) semanticComplexity *= 1.12f;
        if (invocation.composition().occurrencesOf("kringferd") > 0) semanticComplexity *= 1.18f;
        if (invocation.composition().occurrencesOf("ferdafl") > 0) semanticComplexity *= 1.18f;
        if (invocation.composition().hasTargeting(TargetingStyle.REDIRECT)) semanticComplexity *= 1.15f;
        if (invocation.composition().occurrencesOf("med") > 0
                && invocation.composition().occurrencesOf("varn") > 0
                && (invocation.composition().occurrencesOf("sprengja") > 0 || cuttingRing)) {
            semanticComplexity *= 1.18f; // routing a working through an existing barrier
        }
        int targetingInstructions = invocation.composition().targetingStyles().size();
        if (targetingInstructions > 1) semanticComplexity *= 1f + .12f * (targetingInstructions - 1);

        // Weaving, multiplying, and semantic path instructions all cost real effort. A longer
        // sentence itself is never penalized; only the extra work the sentence actually requests is.
        return shapeCost * anchors
            * (1f + 0.45f * (elements - 1))
            * (1f + 0.35f * (count - 1))
            * semanticComplexity;
    }

    @Override
    public EffectResult apply(EffectInvocation invocation) {
        Optional<String> capViolation = CAPS.validateTargets(invocation);
        if (capViolation.isPresent()) {
            return EffectResult.failure(capViolation.get());
        }

        ServerPlayer caster = invocation.caster();
        if (!(caster.level() instanceof ServerLevel level)) {
            return EffectResult.failure("The working slips away.");
        }

        List<Element> elements = resolveElements(invocation);
        if (elements.isEmpty()) {
            return EffectResult.failure(
                "The form is spoken but hollow - the working needs an element to pour into it (eldr, elding, is, ...).");
        }

        SpellShape shape = resolveShape(invocation);
        FormEngine engine = isCuttingRing(invocation) ? AdvancedFormEngines.CUTTING_RING : ENGINES.get(shape);
        if (engine == null) {
            return EffectResult.failure("That form has no working bound to it yet.");
        }

        // Power: how precisely the verb names the act, plus how forcefully
        // the sentence asks for it - clamped to this handler's hard cap.
        float verbPrecision = invocation.composition().wordsOf(WordCategory.VERB).stream()
            .findFirst().map(Word::precision).orElse(0.5f);
        float power = 3f + verbPrecision * 4f + invocation.modifierMagnitudeSum() * 3f;
        power = Math.max(1f, Math.min(power, CAPS.maxMagnitudePerTarget()));

        int count = resolveCount(invocation);

        Vec3 direction = Optional.ofNullable(KineticDirections.combined(invocation))
            .orElse(caster.getLookAngle());

        float scopeRadius = invocation.composition().scopeWord().map(Word::scopeRadius).orElse(0f);

        List<WorkingContext.Anchor> anchors = new ArrayList<>();
        for (EffectTarget target : invocation.targets()) {
            switch (target) {
                case EffectTarget.OfEntity(Entity entity) -> anchors.add(WorkingContext.Anchor.of(entity));
                case EffectTarget.OfBlock(BlockPos pos) -> anchors.add(WorkingContext.Anchor.of(Vec3.atCenterOf(pos)));
                case EffectTarget.OfDirection(Vec3 origin, Vec3 dir) -> anchors.add(WorkingContext.Anchor.of(origin.add(dir)));
            }
        }

        WorkingContext ctx = new WorkingContext(
            caster, level, elements, invocation, power, count,
            resolveTargeting(invocation), direction.normalize(), scopeRadius, anchors
        );

        // samdraga is a generic convergence instruction, not a named spell. Show the spoken
        // concentration before the resolved form launches; every element/form can reuse it.
        if (invocation.composition().occurrencesOf("samdraga") > 0) {
            // Convergence belongs at the working's forming point (the casting hand), not one block
            // in front of the camera.  Bound/area spells can still converge at their own form later.
            Vec3 focus = ctx.origin().add(ctx.handAim(8.0).scale(0.28));
            com.dragonspeech.fx.SpellBodyVfx.emit(level, caster, com.dragonspeech.fx.SpellBodyVfxType.CONVERGENCE,
                elements, focus, focus, 0.72f + power * .045f, 0f,
                invocation.composition().hasContinuousModifier() ? 26 : 12);
        }

        return engine.run(ctx);
    }

    // ============================== Sentence interpretation ==============================

    /**
     * The verb's own baked-in element first (eldingkast IS lightning), then
     * every element noun spoken. Without samvefja only the first element is
     * used; with it, up to MAX_WOVEN_ELEMENTS blend into one working.
     */
    private static List<Element> resolveElements(EffectInvocation invocation) {
        List<Element> found = new ArrayList<>();
        for (Word word : invocation.composition().words()) {
            word.element().ifPresent(element -> {
                if (!found.contains(element)) {
                    found.add(element);
                }
            });
        }

        boolean weave = invocation.composition().hasWeaveWord();
        if (found.size() > 1 && !weave) {
            return List.of(found.get(0));
        }
        return found.size() > MAX_WOVEN_ELEMENTS ? found.subList(0, MAX_WOVEN_ELEMENTS) : found;
    }

    /**
     * Generic casting verbs such as kasta carry a default BOLT form, while a later noun/modifier can
     * refine that form (voddr -> LANCE, fjotbinda -> TETHER, etc.). Prefer those explicit advanced
     * forms over the generic verb default so "kasta eldr voddr" remains one composed fire-lance
     * sentence rather than being trapped as a bolt merely because kasta was encountered first.
     */
    private static SpellShape resolveShape(EffectInvocation invocation) {
        SpellShape first = null;
        for (Word word : invocation.composition().words()) {
            if (word.shape().isEmpty()) continue;
            SpellShape shape = word.shape().get();
            if (first == null) first = shape;
            // These words deliberately refine a generic cast form (for example kasta -> bolt).
            // Motion words such as sveira are NOT shapes: they modify the resolved form instead,
            // which keeps combinations like rain + spiral and ray + spiral compositional.
            if (shape == SpellShape.LANCE || shape == SpellShape.TETHER || shape == SpellShape.CLAW || shape == SpellShape.SHELL) {
                return shape;
            }
        }
        return first != null ? first : SpellShape.BOLT;
    }

    /**
     * Generic moving cutting-ring construct.  This is intentionally recognized from the
     * sentence rather than exposed as a fixed named spell: kasta + hringr + sveira + hvassa.
     * Any spoken element/substance can fill the ring.
     */
    private static boolean isSharpRotatingRing(EffectInvocation invocation) {
        return invocation.composition().occurrencesOf("hringr") > 0
            && invocation.composition().occurrencesOf("sveira") > 0
            && invocation.composition().occurrencesOf("hvassa") > 0;
    }

    private static boolean isCuttingRing(EffectInvocation invocation) {
        return invocation.composition().occurrencesOf("kasta") > 0 && isSharpRotatingRing(invocation);
    }

    private static Optional<TargetingStyle> resolveTargeting(EffectInvocation invocation) {
        return invocation.composition().targeting();
    }

    private static int resolveCount(EffectInvocation invocation) {
        return Math.min(MAX_COUNT, invocation.composition().repeatCount());
    }
}
