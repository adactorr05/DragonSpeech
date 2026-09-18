package com.dragonspeech.mob.casting;

import com.dragonspeech.DragonSpeech;
import com.dragonspeech.spell.CostBreakdown;
import com.dragonspeech.spell.EffortContext;
import com.dragonspeech.spell.SpellComposition;
import com.dragonspeech.spell.SpellCostCalculator;
import com.dragonspeech.word.Word;
import com.dragonspeech.word.WordCategory;
import com.dragonspeech.word.WordRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Builds the best spell sentence a mob can currently afford for a given
 * intent, out of only the words it actually knows (MobVocabulary) - this is
 * the mob-side mirror of a player assembling words in the casting grid,
 * except the "typing" is done by AI instead of a person.
 *
 * Deliberately reuses the real SpellCostCalculator (the exact formula a
 * player's cast is priced with - both Word and SpellComposition are already
 * caster-agnostic, so this needed zero changes to either) rather than
 * inventing a separate mob-only cost curve. That means the core design
 * promise - "a long, precise sentence is cheaper than a short vague one" -
 * naturally makes a mob that knows MORE words (a higher MobPowerTier - see
 * MobWordPools) cast BETTER, not just bigger, spells: it can afford to add
 * a scope word, a matching modifier, a wound/summon noun, where a low-tier
 * mob with a smaller energy pool has to fall back to the bare crude verb.
 *
 * Does NOT go through SpellCastResolver / EffectHandler / EffectInvocation
 * at all. Those three are built entirely around a ServerPlayer caster
 * (inventory-charged items, hunger/health drain, XP, chat feedback...)
 * throughout - re-plumbing 24 existing effect-handler files to accept
 * either a player or a mob was judged too invasive and too risky for this
 * pass to attempt blind. A composed mob spell is instead priced here (using
 * the small BASE_MAGNITUDE table below in place of each EffectHandler's own
 * estimateBaseMagnitude()) and carried out by MobEffectExecutor, a
 * deliberately separate, mob-only execution path.
 */
public final class MobSpellComposer {

    private MobSpellComposer() {}

    /**
     * effectHandlerId path -> "how hard is this, fundamentally" - the same
     * role EffortContext.baseTaskMagnitude plays for a player's cast, just
     * hand-tuned per effect here instead of read off a real EffectHandler.
     * Pure balance knobs, not lore. Only covers the effect ids SpellIntent
     * actually offers to mob AI.
     */
    private static final Map<String, Float> BASE_MAGNITUDE = Map.ofEntries(
        Map.entry("push", 8f),
        Map.entry("shock", 15f),
        Map.entry("ignite", 15f),
        Map.entry("freeze", 15f),
        Map.entry("poison", 12f),
        Map.entry("confuse", 10f),
        Map.entry("petrify", 25f),
        Map.entry("heal", 20f),
        Map.entry("summon", 30f),
        Map.entry("teleport", 10f),
        Map.entry("lift", 8f),
        Map.entry("wall", 15f),
        Map.entry("sunder", 12f),
        Map.entry("hurl_block", 15f),
        Map.entry("pillar", 10f),
        Map.entry("shape_block", 8f)
    );

    public record ComposedSpell(SpellComposition composition, float cost) {}

    public static Optional<ComposedSpell> compose(SpellcastingMob caster, SpellIntent intent, LivingEntity target) {
        return compose(caster, intent, target, caster.mysticalEnergy());
    }

    /**
     * energyBudget: how much of the caster's current energy it's
     * actually willing to spend on THIS cast, which may be less than
     * its full mysticalEnergy() - see MobCastStrategy/MobSpellCastGoal.
     * The 3-arg overload above just spends freely (budget = everything
     * currently available), same as this class always did before
     * stamina management existed.
     */
    public static Optional<ComposedSpell> compose(SpellcastingMob caster, SpellIntent intent, LivingEntity target, float energyBudget) {
        List<Word> known = resolveKnownWords(caster);

        List<Word> verbCandidates = new ArrayList<>(known.stream()
            .filter(w -> w.category() == WordCategory.VERB)
            .filter(w -> w.effectHandlerId().isPresent())
            .filter(w -> intent.accepts(w.effectHandlerId().get().getPath()))
            .toList());

        if (verbCandidates.isEmpty()) {
            return Optional.empty();
        }

        // FIX: "instead of using a variety of spells, they continue to
        // use the same spell" - this list used to be sorted strictly by
        // precision (highest first) and tried in that exact order every
        // single cast, so a mob with a consistently-affordable top
        // candidate would cast THAT one forever and never anything else.
        // Shuffling per attempt means which verb gets tried first (and
        // therefore which one actually gets cast, since the first
        // affordable one wins) varies cast to cast, while affordability
        // still naturally favors higher-precision (cheaper) words more
        // often than not, since those are more likely to fit the energy
        // budget across a random ordering.
        //
        // VERSION-RISK-STYLE NOTE turned real bug: Collections.shuffle
        // only accepts java.util.Random, and RandomSource (Minecraft's
        // own RNG interface, what Entity.getRandom() returns) does NOT
        // implement it - confirmed by a real compile error. Fisher-Yates
        // by hand instead, using RandomSource.nextInt(int) directly.
        shuffleInPlace(verbCandidates, caster.asEntity().getRandom());

        // "They can learn what does damage and can learn how to bypass
        // their wards" per direction - see WardLearner. An advanced
        // Shade that's already seen a verb get blocked against THIS
        // target tries everything else first, and only falls back to
        // the known-blocked verb as a last resort (not excluded
        // entirely - the target's ward might have run out of durability
        // by now, see MobWards.WardInstance, so it could actually work).
        if (caster instanceof WardLearner learner && target != null) {
            List<Word> untried = new ArrayList<>();
            List<Word> knownBlocked = new ArrayList<>();
            for (Word verb : verbCandidates) {
                ResourceLocation verbId = DragonSpeech.id(verb.trueName());
                if (learner.isKnownBlocked(target.getUUID(), verbId)) {
                    knownBlocked.add(verb);
                } else {
                    untried.add(verb);
                }
            }
            verbCandidates = new ArrayList<>(untried.size() + knownBlocked.size());
            verbCandidates.addAll(untried);
            verbCandidates.addAll(knownBlocked);
        }

        float energy = energyBudget;
        float distance = target != null ? (float) caster.asEntity().distanceTo(target) : 0f;

        for (Word verb : verbCandidates) {
            String effectPath = verb.effectHandlerId().get().getPath();
            float baseMagnitude = BASE_MAGNITUDE.getOrDefault(effectPath, 12f);

            Word noun = pickNoun(known, effectPath);
            Word modifier = pickModifier(known, intent);
            Word scope = pickScope(known, target != null);

            for (List<Word> attempt : buildAttempts(verb, noun, modifier, scope)) {
                SpellComposition composition = new SpellComposition(attempt);
                EffortContext effort = new EffortContext(baseMagnitude, distance, false, 1);
                CostBreakdown breakdown = SpellCostCalculator.calculate(composition, effort, 1.0f);
                if (breakdown.finalCost() <= energy) {
                    return Optional.of(new ComposedSpell(composition, breakdown.finalCost()));
                }
            }
        }

        return Optional.empty();
    }

    /**
     * Every affordable-attempt trim of one verb's full sentence, from most
     * to least complete: full sentence, no modifier, verb+scope only, bare
     * verb. Higher-precision words cost LESS per the real formula, so
     * trying the full (most precise) sentence first before falling back is
     * exactly the behavior a careful caster should have.
     */
    private static List<List<Word>> buildAttempts(Word verb, Word noun, Word modifier, Word scope) {
        List<List<Word>> attempts = new ArrayList<>();
        attempts.add(compact(verb, noun, modifier, scope));
        if (modifier != null) {
            attempts.add(compact(verb, noun, null, scope));
        }
        if (noun != null) {
            attempts.add(compact(verb, null, null, scope));
        }
        attempts.add(compact(verb, null, null, null));
        return attempts;
    }

    private static List<Word> compact(Word... words) {
        List<Word> list = new ArrayList<>(words.length);
        for (Word w : words) {
            if (w != null) {
                list.add(w);
            }
        }
        return list;
    }

    /**
     * Only heal (wound-type noun), summon (summon-type noun), and now
     * hurl_block/pillar/shape_block (block-type material noun - jord/
     * steinn/vidr/sandr/eldsteinn/endasteinn) actually use a target noun
     * among the effects mob AI can currently ask for. sunder deliberately
     * has NO case here - same as the real SunderEffectHandler, it breaks
     * whatever's in front of the caster and needs no material named.
     * MobEffectExecutor falls back to BlockType.STONE for the three
     * material effects if a mob doesn't happen to know a matching noun,
     * so this is a nice-to-have precision boost, not a requirement.
     */
    private static Word pickNoun(List<Word> known, String effectPath) {
        return switch (effectPath) {
            case "heal" -> known.stream()
                .filter(w -> w.category() == WordCategory.NOUN_TARGET && w.woundType().isPresent())
                .max(Comparator.comparingDouble(Word::precision))
                .orElse(null);
            case "summon" -> known.stream()
                .filter(w -> w.category() == WordCategory.NOUN_TARGET && w.summonType().isPresent())
                .max(Comparator.comparingDouble(Word::precision))
                .orElse(null);
            case "hurl_block", "pillar", "shape_block" -> known.stream()
                .filter(w -> w.category() == WordCategory.NOUN_TARGET && w.blockType().isPresent())
                .max(Comparator.comparingDouble(Word::precision))
                .orElse(null);
            default -> null;
        };
    }

    /** Prefers the strongest known intensifier for an aggressive intent, or the strongest known diminisher for a careful one (self-healing). Mobility/utility casts don't bother with a magnitude modifier for now. */
    private static Word pickModifier(List<Word> known, SpellIntent intent) {
        boolean wantsPositive = intent == SpellIntent.OFFENSE || intent == SpellIntent.CROWD_CONTROL || intent == SpellIntent.SUMMON_HELP;
        boolean wantsNegative = intent == SpellIntent.SELF_HEAL;
        if (!wantsPositive && !wantsNegative) {
            return null;
        }
        return known.stream()
            .filter(w -> w.category() == WordCategory.MODIFIER)
            .filter(Word::isModifierWord)
            .filter(w -> wantsPositive ? w.modifierMagnitude() > 0f : w.modifierMagnitude() < 0f)
            .max(Comparator.comparingDouble(w -> Math.abs(w.modifierMagnitude())))
            .orElse(null);
    }

    /** "thetta" (this, bound target at hand) when there's a real target; "sjalfan" (oneself) otherwise; falls back to the highest-precision scope word known at all. */
    private static Word pickScope(List<Word> known, boolean hasTarget) {
        String preferred = hasTarget ? "thetta" : "sjalfan";
        return known.stream()
            .filter(w -> w.category() == WordCategory.SCOPE)
            .filter(w -> w.trueName().equals(preferred))
            .findFirst()
            .or(() -> known.stream()
                .filter(w -> w.category() == WordCategory.SCOPE)
                .max(Comparator.comparingDouble(Word::precision)))
            .orElse(null);
    }

    /** Collections.shuffle only accepts java.util.Random - RandomSource (what Entity.getRandom() returns) doesn't implement it. Plain Fisher-Yates instead. */
    private static <T> void shuffleInPlace(List<T> list, net.minecraft.util.RandomSource random) {
        for (int i = list.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            T tmp = list.get(i);
            list.set(i, list.get(j));
            list.set(j, tmp);
        }
    }

    private static List<Word> resolveKnownWords(SpellcastingMob caster) {
        List<Word> words = new ArrayList<>();
        for (ResourceLocation id : caster.vocabulary().words()) {
            Word word = WordRegistry.get(id);
            if (word != null) {
                words.add(word);
            }
        }
        return words;
    }
}
