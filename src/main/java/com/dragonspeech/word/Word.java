package com.dragonspeech.word;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;

/**
 * A single word in the Ancient Language.
 *
 * A word's cost contribution to a spell comes from its precision and
 * domain, NOT from being counted - a spell with fifty precise words
 * should never cost more than one vague word aimed at the same task.
 * See the (Phase 2) SpellCostCalculator for how these fields combine.
 *
 * hashedTrueName is the ONLY field ever used to check a guess. trueName
 * is stored so the server can display it to a player who has already
 * discovered the word (grimoire, chat feedback, etc.) - it must never be
 * sent to a client for a word that player has not unlocked. See
 * WordHashing for why this distinction matters and what its real limits are.
 *
 * effectHandlerId is the security boundary of the whole mod: it's how a
 * VERB word gets turned into an actual game-state change. It can ONLY
 * point at a handler that's been registered in compiled Java code via
 * EffectHandlerRegistry - a datapack can add new words, but it can never
 * add a new kind of effect. No word, however it's phrased or combined,
 * can resolve to anything outside that fixed, hard-capped set of handlers.
 * Non-VERB words (targets, modifiers, scope, control, binding) leave this
 * empty - they shape a cast but don't independently trigger one.
 *
 * THE ELEMENTAL GRAMMAR FIELDS (element / shape / targeting / weave /
 * targetless / repeatCount / tempo) extend that same principle to the composed
 * spell system: a spell is not chosen from a list, it is INTERPRETED from
 * the sentence, and these fields are how words carry their part of the
 * sentence's meaning. Each one can only select from a fixed compiled set
 * (Element, SpellShape, TargetingStyle enums) or a clamped number - see
 * ElementalWorkingHandler for how they combine.
 *
 * CODEC NOTE: RecordCodecBuilder.group() only has overloads up to 16
 * fields, and this record now has 29 (27, plus tool_material/tool_type
 * for the hurled-weapon system - both landed in the Tail segment, which
 * had room to spare under its own 16-field cap). Rather than cram fields together or
 * change the record's public shape, the codec below is built as three
 * MapCodec segments combined with nested Codec.mapPair - the same
 * standard Mojang-codecs pattern the previous two-segment version used.
 * Every accessor (Word::trueName, Word::element, etc.) and every other
 * file in the project is completely unaffected; only this class's
 * internals changed.
 */
public record Word(
    String trueName,
    String hashedTrueName,
    String meaning,
    WordCategory category,
    Domain domain,
    float precision,
    Optional<ResourceLocation> synonymGroup,
    List<ResourceLocation> prerequisiteWords,
    DiscoveryMethod discoveryMethod,
    RiskTier riskTier,
    boolean isModifierWord,
    float modifierMagnitude,
    boolean eldunariAmplified,
    Optional<ResourceLocation> effectHandlerId,
    float scopeRadius,
    Optional<com.dragonspeech.ward.WardType> wardType,
    Optional<String> direction,
    boolean scopeSelf,
    Optional<com.dragonspeech.wound.WoundType> woundType,
    Optional<com.dragonspeech.mob.SummonType> summonType,
    Optional<com.dragonspeech.block.BlockType> blockType,
    Optional<com.dragonspeech.weapon.ToolMaterial> toolMaterial,
    Optional<com.dragonspeech.weapon.ToolType> toolType,
    boolean targetless,
    int repeatCount,
    Optional<com.dragonspeech.engine.SpellShape> shape,
    Optional<com.dragonspeech.engine.Element> element,
    Optional<com.dragonspeech.engine.TargetingStyle> targeting,
    boolean weave,
    float tempo
) {
    private record Head(
        String trueName,
        String hashedTrueName,
        String meaning,
        WordCategory category,
        Domain domain,
        float precision,
        Optional<ResourceLocation> synonymGroup,
        List<ResourceLocation> prerequisiteWords,
        DiscoveryMethod discoveryMethod
    ) {
        static final MapCodec<Head> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf("true_name").forGetter(Head::trueName),
            Codec.STRING.fieldOf("hashed_true_name").forGetter(Head::hashedTrueName),
            Codec.STRING.fieldOf("meaning").forGetter(Head::meaning),
            WordCategory.CODEC.fieldOf("category").forGetter(Head::category),
            Domain.CODEC.fieldOf("domain").forGetter(Head::domain),
            Codec.floatRange(0.0f, 1.0f).fieldOf("precision").forGetter(Head::precision),
            ResourceLocation.CODEC.optionalFieldOf("synonym_group").forGetter(Head::synonymGroup),
            ResourceLocation.CODEC.listOf().optionalFieldOf("prerequisite_words", List.of()).forGetter(Head::prerequisiteWords),
            DiscoveryMethod.CODEC.optionalFieldOf("discovery_method", DiscoveryMethod.RUIN_TABLET).forGetter(Head::discoveryMethod)
        ).apply(instance, Head::new));
    }

    private record Tail(
        RiskTier riskTier,
        boolean isModifierWord,
        float modifierMagnitude,
        boolean eldunariAmplified,
        Optional<ResourceLocation> effectHandlerId,
        float scopeRadius,
        Optional<com.dragonspeech.ward.WardType> wardType,
        Optional<String> direction,
        boolean scopeSelf,
        Optional<com.dragonspeech.wound.WoundType> woundType,
        Optional<com.dragonspeech.mob.SummonType> summonType,
        Optional<com.dragonspeech.block.BlockType> blockType,
        Optional<com.dragonspeech.weapon.ToolMaterial> toolMaterial,
        Optional<com.dragonspeech.weapon.ToolType> toolType
    ) {
        static final MapCodec<Tail> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            RiskTier.CODEC.optionalFieldOf("risk_tier", RiskTier.TRIVIAL).forGetter(Tail::riskTier),
            Codec.BOOL.optionalFieldOf("is_modifier_word", false).forGetter(Tail::isModifierWord),
            // Only meaningful when isModifierWord is true. Positive = intensifier
            // ("greatly") pushes cost AND intended effect magnitude up; negative =
            // diminisher ("slightly") pushes both down. Neutral manner-modifiers
            // (e.g. "swiftly", which changes timing rather than magnitude) can
            // stay at 0 - see SpellCostCalculator and EffectHandlers,
            // which should both read this same number so cost and outcome never
            // disagree with each other.
            Codec.floatRange(-1.0f, 1.0f).optionalFieldOf("modifier_magnitude", 0.0f).forGetter(Tail::modifierMagnitude),
            Codec.BOOL.optionalFieldOf("eldunari_amplified", false).forGetter(Tail::eldunariAmplified),
            ResourceLocation.CODEC.optionalFieldOf("effect_handler").forGetter(Tail::effectHandlerId),
            // Only meaningful on SCOPE words. 0 = "the single thing I am
            // looking at" (thetta). Greater than 0 = "everything within this
            // many blocks of me" - the word itself carries how wide its net
            // is, so new area-scopes are pure datapack content, no code.
            Codec.floatRange(0f, 16f).optionalFieldOf("scope_radius", 0f).forGetter(Tail::scopeRadius),
            // Only meaningful on BINDING words: which damage category a ward
            // raised with this word intercepts. Data-driven for the same
            // reason as scope_radius - new ward types per word are pure JSON.
            com.dragonspeech.ward.WardType.CODEC.optionalFieldOf("ward_type").forGetter(Tail::wardType),
            // Only meaningful on directional MODIFIER words: "up", "down",
            // "forward", "back". Kinetic handlers combine every direction word
            // in the sentence into one vector - "uppa ok frama" pushes up AND
            // forward, exactly as spoken.
            Codec.STRING.optionalFieldOf("direction").forGetter(Tail::direction),
            // Only meaningful on SCOPE words: true = the working turns on the
            // speaker themself ("sjalfan"). "graeda sjalfan" heals YOU.
            Codec.BOOL.optionalFieldOf("scope_self", false).forGetter(Tail::scopeSelf),
            // Only meaningful on NOUN words used in healing: which KIND of
            // wound this noun names. "graeda bein sjalfan" mends broken
            // bones and nothing else - see HealEffectHandler.
            com.dragonspeech.wound.WoundType.CODEC.optionalFieldOf("wound_type").forGetter(Tail::woundType),
            // Only meaningful on NOUN words used in summoning: which
            // creature this noun names ("beinvaettr" -> Skeleton). A word
            // can only select from SummonType's fixed, compiled set -
            // exactly the same hard boundary effect_handler enforces for
            // verbs. See SummonEffectHandler.
            com.dragonspeech.mob.SummonType.CODEC.optionalFieldOf("summon_type").forGetter(Tail::summonType),
            // Only meaningful on NOUN words used in shaping/conjuring
            // matter: which vanilla block this noun names ("steinn" ->
            // Stone). Same fixed-set boundary as summon_type. See
            // ShapeBlockEffectHandler.
            com.dragonspeech.block.BlockType.CODEC.optionalFieldOf("block_type").forGetter(Tail::blockType),
            // Only meaningful on NOUN words used in the hurled-weapon
            // system: which material this noun names ("jarn" -> IRON).
            // Same fixed-set boundary as block_type/summon_type. Absent
            // on a tool noun (or on the whole sentence) defaults to WOOD,
            // the weakest tier - see HurlWeaponEffectHandler.
            com.dragonspeech.weapon.ToolMaterial.CODEC.optionalFieldOf("tool_material").forGetter(Tail::toolMaterial),
            // Only meaningful on NOUN words used in the hurled-weapon
            // system: which tool/weapon shape this noun names ("sverd" ->
            // SWORD). A hurling with no tool_type noun spoken at all has
            // nothing to throw and fails - see HurlWeaponEffectHandler.
            com.dragonspeech.weapon.ToolType.CODEC.optionalFieldOf("tool_type").forGetter(Tail::toolType)
        ).apply(instance, Tail::new));
    }

    /** The composed-spell grammar segment - see the class comment. */
    private record Grammar(
        boolean targetless,
        int repeatCount,
        Optional<com.dragonspeech.engine.SpellShape> shape,
        Optional<com.dragonspeech.engine.Element> element,
        Optional<com.dragonspeech.engine.TargetingStyle> targeting,
        boolean weave,
        float tempo
    ) {
        static final MapCodec<Grammar> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            // "marklaust": this word releases the sentence from needing a
            // bound target - the cast may go out along a direction instead.
            Codec.BOOL.optionalFieldOf("targetless", false).forGetter(Grammar::targetless),
            // "margfalt"/"tvefalt": how many times the working repeats
            // (bolts fired, marks placed, creatures called). Handlers clamp
            // this against their own caps; the highest spoken count wins.
            Codec.intRange(1, 8).optionalFieldOf("repeat_count", 1).forGetter(Grammar::repeatCount),
            // Form verbs (kasta/geisla/kula/...): which FormEngine shapes
            // the working. Fixed compiled set - see SpellShape.
            com.dragonspeech.engine.SpellShape.CODEC.optionalFieldOf("shape").forGetter(Grammar::shape),
            // Element nouns (eldr/elding/is/...) and element-flavoured
            // verbs (eldingkast): what the working is made of. Fixed
            // compiled set - see Element.
            com.dragonspeech.engine.Element.CODEC.optionalFieldOf("element").forGetter(Grammar::element),
            // "leitbinda"/"kedjubinda": homing / chain behavior. Fixed
            // compiled set - see TargetingStyle.
            com.dragonspeech.engine.TargetingStyle.CODEC.optionalFieldOf("targeting").forGetter(Grammar::targeting),
            // "samvefja": permits weaving more than one element into a
            // single working (still capped in ElementalWorkingHandler).
            Codec.BOOL.optionalFieldOf("weave", false).forGetter(Grammar::weave),
            // Temporal lean: "seint" -1 (slower), "snoggt" +1 (swifter),
            // "hradi" +0.5, "kyrra" -2 (stilled utterly - full stasis).
            // TemporalWorkingHandler sums every spoken tempo to decide
            // whether a time-binding hastens, slows, or stops its mark.
            Codec.floatRange(-2.0f, 2.0f).optionalFieldOf("tempo", 0.0f).forGetter(Grammar::tempo)
        ).apply(instance, Grammar::new));
    }

    public static final Codec<Word> CODEC = Codec.mapPair(Head.CODEC, Codec.mapPair(Tail.CODEC, Grammar.CODEC)).xmap(
        pair -> {
            Head head = pair.getFirst();
            Tail tail = pair.getSecond().getFirst();
            Grammar grammar = pair.getSecond().getSecond();
            return new Word(
                head.trueName(), head.hashedTrueName(), head.meaning(),
                head.category(), head.domain(), head.precision(),
                head.synonymGroup(), head.prerequisiteWords(), head.discoveryMethod(),
                tail.riskTier(), tail.isModifierWord(), tail.modifierMagnitude(),
                tail.eldunariAmplified(), tail.effectHandlerId(), tail.scopeRadius(),
                tail.wardType(), tail.direction(), tail.scopeSelf(), tail.woundType(),
                tail.summonType(), tail.blockType(), tail.toolMaterial(), tail.toolType(),
                grammar.targetless(), grammar.repeatCount(), grammar.shape(),
                grammar.element(), grammar.targeting(), grammar.weave(), grammar.tempo()
            );
        },
        word -> Pair.of(
            new Head(word.trueName(), word.hashedTrueName(), word.meaning(), word.category(), word.domain(),
                word.precision(), word.synonymGroup(), word.prerequisiteWords(), word.discoveryMethod()),
            Pair.of(
                new Tail(word.riskTier(), word.isModifierWord(), word.modifierMagnitude(), word.eldunariAmplified(),
                    word.effectHandlerId(), word.scopeRadius(), word.wardType(), word.direction(), word.scopeSelf(), word.woundType(),
                    word.summonType(), word.blockType(), word.toolMaterial(), word.toolType()),
                new Grammar(word.targetless(), word.repeatCount(), word.shape(), word.element(), word.targeting(), word.weave(), word.tempo())
            )
        )
    ).codec();

    /** Convenience check used constantly by the (Phase 2) grid validator and (Phase 3) guess resolver. */
    public boolean matchesGuess(String candidate) {
        return WordHashing.matches(candidate, hashedTrueName);
    }
}
