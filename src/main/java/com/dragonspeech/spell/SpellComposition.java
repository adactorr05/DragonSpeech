package com.dragonspeech.spell;

import com.dragonspeech.engine.SpellShape;
import com.dragonspeech.engine.TargetingStyle;
import com.dragonspeech.word.Domain;
import com.dragonspeech.word.Word;
import com.dragonspeech.word.WordCategory;

import java.util.List;
import java.util.Optional;

/**
 * An ordered, grammar-structured group of words a player has assembled -
 * either through the casting grid or by successfully guessing/typing a
 * full spell. This is the input to SpellCostCalculator.
 *
 * IMPORTANT: by the time a SpellComposition exists, grammar VALIDATION
 * (is this actually a legal sentence - does it have a verb, are prerequisite
 * words satisfied, etc.) is assumed to already be done. That validation
 * belongs to the casting grid / guess resolver, not here. This class only
 * answers questions the cost formula and the effect handlers need.
 */
public record SpellComposition(List<Word> words) {

    public List<Word> wordsOf(WordCategory category) {
        return words.stream().filter(w -> w.category() == category).toList();
    }

    public Optional<Word> scopeWord() {
        return wordsOf(WordCategory.SCOPE).stream().findFirst();
    }

    /**
     * Average precision across the words that actually describe WHAT is
     * being done (verb / target / modifier / binding). Scope is excluded
     * on purpose - it has its own dedicated ambiguity term in the cost
     * formula. Control words (the stop-word, etc.) are excluded entirely;
     * they're grammar utility, not part of the task's magnitude.
     */
    public float averageMagnitudePrecision() {
        List<Word> relevant = words.stream()
                .filter(w -> w.category() != WordCategory.SCOPE && w.category() != WordCategory.CONTROL)
                .toList();

        if (relevant.isEmpty()) {
            return 0.0f;
        }

        float sum = 0f;
        for (Word w : relevant) {
            sum += w.precision();
        }
        return sum / relevant.size();
    }

    /**
     * Sum of every MODIFIER word's directional magnitude, clamped to a
     * sane range so stacking many modifiers can't produce absurd numbers.
     * EffectHandlers read this same value to scale their actual in-game
     * output, so a caster always pays for what they asked for.
     */
    public float modifierMagnitudeSum() {
        float sum = 0f;
        for (Word w : wordsOf(WordCategory.MODIFIER)) {
            sum += w.modifierMagnitude();
        }
        return clamp(sum, -1.0f, 2.0f);
    }

    public boolean hasControlWord() {
        return !wordsOf(WordCategory.CONTROL).isEmpty();
    }

    /**
     * "stodugt" ("with constant, unwavering force") per explicit
     * direction: "I don't see a word that means maintain/sustain/
     * persist/continue/constantly/while/until" - stodugt was already in
     * the dictionary (precision 0.95, Force domain) but had never
     * actually been wired to DO anything mechanically - the one
     * FORCE-domain modifier with no magnitude note at all, unlike
     * ofsa/tvefalt/litla/mikla which all have one. That's exactly the
     * "some words don't do anything" pattern - the word existed, it
     * just wasn't connected to a behavior.
     *
     * Wired here as the GENERIC channel trigger - see
     * CastRequestHandler's dispatch, which now starts a channel
     * (ChannelManager) for ANY verb when this is spoken, not just the
     * one bespoke handler (channel_push/"haldthrysta") that previously
     * had exclusive access to that mechanism.
     */
    public boolean hasContinuousModifier() {
        return occurrencesOf("stodugt") > 0;
    }

    /** The domain that determines which Attunement discount applies - same word (the verb) that determines the effect handler, for consistency. */
    public Optional<Domain> dominantDomain() {
        return wordsOf(WordCategory.VERB).stream().findFirst().map(Word::domain);
    }

    /** Every direction tag spoken in the sentence ("up"/"down"/"forward"/"back"), in order. Kinetic handlers combine these into one vector. */
    public List<String> directionTags() {
        return words.stream()
                .map(Word::direction)
                .flatMap(Optional::stream)
                .toList();
    }

    // ============================== Composed-spell grammar ==============================

    /** True if "marklaust" (or any targetless word) was spoken - the sentence released itself from needing a bound target. */
    public boolean isTargetless() {
        return words.stream().anyMatch(Word::targetless);
    }

    /** The highest spoken repeat count ("margfalt" -> 3), at least 1. Handlers clamp this against their own caps. */
    public int repeatCount() {
        return words.stream().mapToInt(Word::repeatCount).max().orElse(1);
    }

    /** The first spoken FORM in the sentence (kasta/geisla/kula/...). A bare element-verb has none and defaults to BOLT in the handler. */
    public Optional<SpellShape> shape() {
        return words.stream().map(Word::shape).flatMap(Optional::stream).findFirst();
    }

    /** The first spoken targeting style, retained for older callers that only need one style. */
    public Optional<TargetingStyle> targeting() {
        return words.stream().map(Word::targeting).flatMap(Optional::stream).findFirst();
    }

    /** Every targeting instruction spoken in the sentence, in word order. */
    public List<TargetingStyle> targetingStyles() {
        return words.stream().map(Word::targeting).flatMap(Optional::stream).distinct().toList();
    }

    /** Targeting instructions compose: a working may be homing AND chaining AND redirected. */
    public boolean hasTargeting(TargetingStyle style) {
        return words.stream().map(Word::targeting).flatMap(Optional::stream).anyMatch(t -> t == style);
    }

    /** True if "samvefja" (or any weave word) was spoken - more than one element may be woven into the working. */
    public boolean hasWeaveWord() {
        return words.stream().anyMatch(Word::weave);
    }

    /**
     * How many times a word with this exact true_name appears in the
     * sentence, spoken verbatim more than once. This is the hook for
     * "repeatable" words like margfalt or afla: saying the SAME word
     * again is a deliberate, opt-in way to ask for more of exactly what
     * that word does - it is not the same thing as a sentence simply
     * having more words in it (which SpellCostCalculator explicitly
     * never charges for). See RepetitionCost for how the escalating
     * price of doing this is computed.
     */
    public int occurrencesOf(String trueName) {
        int count = 0;
        for (Word w : words) {
            if (w.trueName().equalsIgnoreCase(trueName)) {
                count++;
            }
        }
        return count;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}