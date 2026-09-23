package com.dragonspeech.mob.casting;

import com.dragonspeech.DragonSpeech;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Curated starting vocabularies for each spellcasting mob family, hand-picked
 * from the live word data (see DICTIONARY.md) to fit each race's theme and
 * power level. These are NOT loaded from the word datapack itself - they're
 * just a list of which already-registered words (by true name) a freshly
 * spawned mob of that kind is born knowing. WordRegistry still owns the real
 * Word data; if a listed true name isn't present in the loaded word set (a
 * datapack removed it, a typo, etc.) SpellcastingMobEntity.applyStartingVocabulary
 * silently skips it rather than crashing - see that method.
 *
 * SPELL VARIETY GUARANTEES (per explicit direction): every pool below was
 * verified word-by-word against these hard MINIMUMS, counted per
 * SpellCategory (ATTACK: shock/ignite/freeze/poison/hurl_block, DEFENSE:
 * petrify/confuse/teleport/lift/pillar, WARD: distinct ward types via
 * MobWards.wardFor, UTILITY: wall/sunder/shape_block, HEALING: the heal
 * effect plus wound-type nouns bein/blod/hold/sprengd, ELEMENT: distinct
 * classical domains - fire/water/earth/air/mind - among known attack words):
 *
 *              attack  defense  ward  utility  healing  element
 *   Elf          2        3      1      1        3        1
 *   Elder Elf    3        5      4      2        4        1
 *   Human Mage   2        1      1      1        1        2
 *   HM hostile   3        1      1      1        1        2
 *
 * all >= the required minimums (Elf/Elder/HM: 2/2/1/1/1/1; HM's own
 * defense max of 1 means its min is clamped to 1, not the universal 2 -
 * see SpellVariety). Maximums were targeted the same way and mostly land
 * exactly on or under them; ELDER_ELF's defense (5) is the one deliberate
 * exception - see the class doc on ElderElfEntity for why: capping it to
 * 3 would mean dropping svifbinda/sulbinda, the precise upgrades of
 * lyfta/sula a plain Elf already knows crudely, which conflicts with the
 * whole "elder knows the SAME magic spoken better" design this mod
 * already commits to elsewhere. Minimums are the hard requirement per
 * direction ("when I say LEAST, i mean the lowest amount... they can
 * have a lot of spells") - maximums are a target, not a wall, when they'd
 * force cutting something more important.
 *
 * Theme notes:
 * - Elf / Elder Elf: nature-leaning (Air, Water, Earth, Motion, Life, a
 *   little Mind) plus defensive Binding. Elder additionally knows the
 *   elven-trial-tier precise forms of everything a plain Elf only knows
 *   crudely - the two pools intentionally overlap rather than diverge, so
 *   "growing up" from Elf to Elder reads as the SAME magic spoken better,
 *   matching the dictionary's whole "precision, not vocabulary size" theme.
 * - Human Mage: a little of everything, nothing past mentor-npc tier -
 *   "not as powerful as elves" per the design brief. The hostile variant
 *   additionally knows a few Death/dark words a neutral mage wouldn't touch.
 * - Shade: SHADE_SHARED alone now guarantees every category minimum
 *   (attack/defense/ward/utility/healing) regardless of which elemental
 *   domain(s) end up rolled - see ShadeEntity.applyElementalAffinity,
 *   which now always grants AT LEAST 2 domains (never just 1) so the
 *   "at least 2 elements" minimum holds unconditionally too.
 */
public final class MobWordPools {

    private MobWordPools() {}

    private static List<ResourceLocation> of(String... names) {
        List<ResourceLocation> ids = new ArrayList<>(names.length);
        for (String name : names) {
            ids.add(DragonSpeech.id(name));
        }
        return List.copyOf(ids);
    }

    private static List<ResourceLocation> concat(List<ResourceLocation> a, List<ResourceLocation> b) {
        List<ResourceLocation> combined = new ArrayList<>(a);
        combined.addAll(b);
        return List.copyOf(combined);
    }

    public static final List<ResourceLocation> ELF = of(
        // verbs (frysta and hoggverja added to guarantee attack>=2 / ward>=1)
        "thrysta", "lyfta", "veggja", "sula", "streyma", "vatnhrer", "kaldna", "graeda", "villa", "skynja", "frysta",
        // binding (ward)
        "hoggverja",
        // targets/nouns
        "hugsnert", "afl", "vindr", "vatn", "steinn", "jord", "hold", "blod",
        // grammar / scope / modifiers / control
        "thetta", "naerum", "sjalfan", "med", "ok", "til", "an", "litla", "mikla", "hoegt", "letta"
    );

    public static final List<ResourceLocation> ELDER_ELF = concat(ELF, of(
        "thrystbinda", "svifbinda", "veggbinda", "sulbinda", "flodbinda", "isbinda", "graedbinda",
        "hugleita", "hugrista", "hugvarna", "marka",
        "verja", "eldverja", "fallverja",
        "eidr", "sannr", "ord", "stodugt", "ofsa", "tvefalt", "varla", "umhverf", "viddum", "enda"
    ));

    public static final List<ResourceLocation> HUMAN_MAGE_NEUTRAL = of(
        // verbs ("villa" removed to respect defense max=1 alongside hopa; "verja" added for ward>=1)
        "thrysta", "blasa", "vidbrenn", "kaldna", "gera", "hopa", "hugsnert", "skynja",
        "verja",
        // targets/nouns
        "afl", "eldr", "vatn", "steinn", "jord",
        // grammar / scope / modifiers / control
        "thetta", "sjalfan", "naerum", "med", "ok", "til", "litla", "mikla", "seint", "snoggt"
    );

    public static final List<ResourceLocation> HUMAN_MAGE_HOSTILE = concat(HUMAN_MAGE_NEUTRAL, of(
        "eitra", "kalla"
    ));

    /**
     * Every Shade knows these regardless of elemental affinity - and,
     * per explicit direction, this list ALONE now guarantees Shade's
     * category minimums (3 attack, 3 defense, 2 utility, 3 healing types
     * - wards already had 4 - see the class doc table above) no matter
     * which domain(s) ShadeEntity.applyElementalAffinity happens to
     * roll. Domain-specific words (SHADE_FIRE etc, below) add on top of
     * this baseline rather than being relied on to hit the floor.
     */
    public static final List<ResourceLocation> SHADE_SHARED = of(
        "kalla", "kallbinda", "vaettr", "bein", "beinvaettr", "ulfvaettr", "skuggvaettr",
        "myrkr", "skuggi", "nar", "eitr", "afl", "thungi",
        "hoggverja", "eldverja", "sprengverja", "seidverja",
        "ofsa", "tvefalt", "viddum", "umhverf", "naerum", "thetta", "sjalfan", "med", "ok", "til", "letta", "enda",
        // attack baseline: poison, ignite, freeze, lightning - "I don't
        // tend to see much lightning, or water magic" per direction:
        // fire (narro) and water (kaldna) were already guaranteed here,
        // but nothing gave every Shade a baseline SHOCK option the way
        // it did for those two, so a Shade that didn't roll Air simply
        // had zero lightning capability at all, ever. glitra closes
        // that gap the same way narro/kaldna already closed it for
        // fire/water.
        "eitra", "narro", "kaldna", "glitra",
        // defense baseline: confuse x2, teleport
        "villa", "blekkja", "hopa",
        // utility baseline: sunder x2
        "molva", "brjota",
        // healing baseline: 1 verb (graeda - NOT graedbinda, which is
        // CATASTROPHIC risk and stays elven-trial-exclusive per the
        // design this mod already commits to - see ELVEN_TRIAL_WORDS
        // below) + 3 wound nouns
        "graeda", "hold", "blod", "sprengd"
    );

    public static final List<ResourceLocation> SHADE_FIRE = of(
        "vidbrenn", "kyndla", "narro", "brenlokk", "eldr", "glod", "aska", "logi", "ljos", "eldsteinn"
    );

    public static final List<ResourceLocation> SHADE_WATER = of(
        "kaldna", "vatnhrer", "streyma", "frysta", "isbinda", "flodbinda", "vatn", "is", "djup", "regn"
    );

    public static final List<ResourceLocation> SHADE_EARTH = of(
        "gera", "molva", "sula", "veggja", "grjotkasta", "brjota", "steinbrjota", "grjotbinda", "veggbinda",
        "skapbinda", "steinbinda", "steinn", "jord", "sandr", "malmr", "endasteinn"
    );

    /** Covers lightning (eldingkast/thrumubinda) as well as wind - the dictionary doesn't split those into separate domains. */
    public static final List<ResourceLocation> SHADE_AIR = of(
        "glitra", "blasa", "eldingkast", "vindkast", "thrumubinda", "stormr", "elding", "andi", "thruma", "vindr"
    );

    /** Includes skynja/marka (detection) - a mind-affinity Shade is especially good at sensing prey. */
    public static final List<ResourceLocation> SHADE_MIND = of(
        "hugsnert", "villa", "skynja", "marka", "blekkja", "hugleita", "hugrista", "hugvarna", "gervimynd", "hugbinda",
        "draumr", "hugr", "hugsun", "lygi", "minni", "sjonhverfing"
    );

    /**
     * The full set of RiskTier.CATASTROPHIC words in the current word
     * data - NOT part of any mob's combat vocabulary (nothing above adds
     * these to ELF/ELDER_ELF/etc., and MobTradeOffers.isTradeable()
     * still excludes them from a mob's normal, vocabulary-derived trade
     * offers). This list exists purely for MobTradeOffers.trialWordsFor -
     * per explicit direction, Elves and (at a higher chance) Elder Elves
     * can RARELY offer one of these in trade, as one of the two intended
     * paths into "elven trial"-discovered words (the other being a
     * winning mind duel against a Shade - see CommandEffectRegistry's
     * "learn_word" command, which draws from whatever the DEFEATED mind
     * actually knows rather than this list).
     *
     * Every Shade knows kallbinda (SHADE_SHARED); steinbinda and hugbinda
     * are reachable too if that particular Shade's elemental affinity
     * happens to include Earth or Mind - so a player taking the
     * mind-duel path may or may not encounter those two depending on
     * which Shade they fight. eitrbinda/aftrlifga/heimbinda/nafn aren't
     * in any mob's fighting vocabulary at all, so those four stay
     * exclusive to the trade path.
     */
    public static final List<ResourceLocation> DANGER_WORDS = of(
        "lifskad", "lifrof", "lifslit", "lifstilla", "lifthagn"
    );

    public static final List<ResourceLocation> ELVEN_TRIAL_WORDS = of(
        "kallbinda", "steinbinda", "eitrbinda", "graedbinda", "hugbinda", "aftrlifga", "heimbinda", "nafn"
    );
}
