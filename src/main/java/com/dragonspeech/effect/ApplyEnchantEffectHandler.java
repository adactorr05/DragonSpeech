package com.dragonspeech.effect;

import com.dragonspeech.DragonSpeech;
import com.dragonspeech.enchant.EnchantmentKind;
import com.dragonspeech.enchant.ItemCategory;
import com.dragonspeech.enchant.MagicEnchantment;
import com.dragonspeech.enchant.MagicEnchantments;
import com.dragonspeech.enchant.VanillaEnchantWords;
import com.dragonspeech.enchant.WardPowerSource;
import com.dragonspeech.storage.DragonSpeechComponents;
import com.dragonspeech.storage.SkillsAccess;
import com.dragonspeech.ward.WardType;
import com.dragonspeech.word.Word;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * REDESIGNED: "gala" is now the ONLY enchantment verb with a real
 * effect_handler. Every specific enchant word (galdrverja, the 4 typed
 * ward words, all 14 blessing/curse words including duljabol, all 10
 * vanilla enchant words) lost its OWN effect_handler and is now an
 * argument-only word - meaningless alone, meaningful only spoken
 * alongside "gala." This replaces ApplyWardEnchantEffectHandler,
 * ApplyBlessingOrCurseEffectHandler, ApplyVanillaEnchantEffectHandler,
 * and ApplyHidingCurseEffectHandler as the thing actually registered -
 * those 4 classes are left in the codebase unused rather than deleted
 * (lower risk than tracing every possible reference before removing
 * them; they cost nothing sitting idle).
 *
 * GRAMMAR: "gala <kind-word> [<sub-type-word>] [aflbinda] [letta]
 * [magnitude-modifiers...]"
 *   - "gala galdrverja"              -> generic ward
 *   - "gala galdrverja fallgaldr"    -> fall-specific ward (explicit form)
 *   - "gala fallgaldr"               -> fall-specific ward (short form -
 *     a typed ward word implies "this is a ward" all on its own, since
 *     it can't mean anything else)
 *   - "gala vaengheill"              -> Blessing of the Bonded Wing
 *   - "gala hvassa"                  -> vanilla Sharpness
 *   - "gala duljabol ennisbol"       -> conceal the Marked Brow curse
 *   - "gala fallgaldr mikla"         -> fall ward, durability scaled up
 *   - "gala fallgaldr mikla mikla"   -> same ward, scaled up FURTHER -
 *     modifiers stack additively (mikla = +0.6 each), matching how
 *     "thrystbinda uppa ok frama" already composes elsewhere in this
 *     grammar - "words tie into each other," not fixed spell presets.
 *   - "letta gala <word>"            -> dispel a ward or vanilla enchant
 *     named by <word> (blessings/curses have no dispel path - same
 *     permanence rule as before)
 *
 * MAGNITUDE MODIFIERS scale different things depending on WHAT'S being
 * enchanted:
 *   - WARD: durability pool scales by (1 + summed magnitude), floored
 *     at 10% of base so a heavy litla stack can't zero it out entirely.
 *   - BLESSING / CURSE / VANILLA ENCHANT: summed magnitude rounds to how
 *     many LEVELS to add in one cast (still capped at the usual max),
 *     instead of always adding exactly 1 - "mikla mikla" jumps 2 levels
 *     in one working instead of needing two separate castings.
 */
public class ApplyEnchantEffectHandler implements EffectHandler {

    private static final float ENCHANT_BASE_COST = 120f;
    private static final float WARD_DURABILITY_BASE = 200f;

    private static final Map<String, WardType> WARD_TYPE_WORDS = Map.of(
        "eldgaldr", WardType.FIRE,
        "hoggaldr", WardType.MELEE,
        "sprengaldr", WardType.EXPLOSION,
        "fallgaldr", WardType.FALL
    );

    private static final Map<String, Integer> BLESSING_MAX_LEVEL = Map.ofEntries(
        Map.entry("vaengheill", 2),
        Map.entry("kyrrafl", 3),
        Map.entry("merkiheill", 2),
        Map.entry("arinheill", 3),
        Map.entry("nafnheill", 2),
        Map.entry("handheill", 3),
        Map.entry("lifheill", 1)
    );

    private static final Set<String> CURSE_WORDS = Set.of(
        "ennisbol", "hungrord", "hugopna", "thunnhula", "grafleysa", "visnatak", "haugbol"
    );

    private static final Map<String, Set<String>> VANILLA_EXCLUSIVE_WITH = Map.of(
        "mjukhond", Set.of("gaefa"),
        "gaefa", Set.of("mjukhond")
    );

    private static final EffectHandlerCaps CAPS = new EffectHandlerCaps(
        1, ENCHANT_BASE_COST, 1f, Set.of(TargetKind.ENTITY)
    );

    @Override
    public ResourceLocation id() {
        return DragonSpeech.id("apply_enchant");
    }

    @Override
    public EffectHandlerCaps caps() {
        return CAPS;
    }

    @Override
    public boolean selfTargeting() {
        return true;
    }

    @Override
    public float estimateBaseMagnitude(EffectInvocation invocation) {
        boolean dispel = invocation.composition().words().stream().anyMatch(w -> "letta".equals(w.trueName()));
        return dispel ? 2f : ENCHANT_BASE_COST;
    }

    @Override
    public EffectResult apply(EffectInvocation invocation) {
        ServerPlayer caster = invocation.caster();

        if (!SkillsAccess.get(caster).canEnchant()) {
            return EffectResult.failure(
                "You do not yet know how to bind a working into an object - the shape of the word slips away from you.");
        }

        ItemStack held = caster.getMainHandItem();
        if (held.isEmpty()) {
            return EffectResult.failure("You hold nothing to bind a working into.");
        }

        List<Word> words = invocation.composition().words();
        Set<String> trueNames = words.stream().map(Word::trueName).collect(java.util.stream.Collectors.toSet());
        boolean dispel = trueNames.contains("letta");
        boolean staminaLinked = trueNames.contains("aflbinda");
        boolean reserveLinked = trueNames.contains("afla");

        float magnitudeSum = (float) words.stream()
            .filter(Word::isModifierWord)
            .mapToDouble(Word::modifierMagnitude)
            .sum();
        float magnitudeMultiplier = 1f + magnitudeSum;

        // Which kind of working is this sentence naming? Checked in this
        // order: Hiding (needs its own two-word handling) -> Ward (typed
        // word implies it on its own, or galdrverja explicitly) ->
        // Blessing/Curse -> Vanilla enchant.
        if (trueNames.contains("duljabol")) {
            return applyHiding(held, trueNames);
        }

        Optional<String> wardTypeWord = trueNames.stream().filter(WARD_TYPE_WORDS::containsKey).findFirst();
        boolean genericWard = trueNames.contains("galdrverja");
        if (wardTypeWord.isPresent() || genericWard) {
            String wordId = wardTypeWord.orElse("galdrverja");
            WardType type = wardTypeWord.map(WARD_TYPE_WORDS::get).orElse(null);
            return dispel
                ? applyWardDispel(held, wordId)
                : applyWard(caster, held, wordId, type, staminaLinked, reserveLinked, magnitudeMultiplier);
        }

        Optional<String> blessingWord = trueNames.stream().filter(BLESSING_MAX_LEVEL::containsKey).findFirst();
        if (blessingWord.isPresent()) {
            return applyBlessingOrCurse(held, blessingWord.get(), EnchantmentKind.BLESSING,
                BLESSING_MAX_LEVEL.get(blessingWord.get()), magnitudeMultiplier);
        }

        Optional<String> curseWord = trueNames.stream().filter(CURSE_WORDS::contains).findFirst();
        if (curseWord.isPresent()) {
            return applyBlessingOrCurse(held, curseWord.get(), EnchantmentKind.CURSE, 1, magnitudeMultiplier);
        }

        Optional<String> vanillaWord = trueNames.stream().filter(w -> VanillaEnchantWords.enchantIdFor(w) != null).findFirst();
        if (vanillaWord.isPresent()) {
            return dispel
                ? applyVanillaDispel(held, vanillaWord.get())
                : applyVanilla(caster, held, vanillaWord.get(), magnitudeMultiplier);
        }

        return EffectResult.failure(
            "You must name what working this should be - a ward, a blessing, a curse, or a true enchantment.");
    }

    // ============================== Ward ==============================

    private EffectResult applyWard(ServerPlayer caster, ItemStack held, String wordId, WardType type,
                                   boolean staminaLinked, boolean reserveLinked, float magnitudeMultiplier) {
        WardPowerSource powerSource = staminaLinked ? WardPowerSource.STAMINA_LINKED
            : (reserveLinked ? WardPowerSource.RESERVE : WardPowerSource.DURATION);
        float reserveMax = Math.max(WARD_DURABILITY_BASE * 0.1f, WARD_DURABILITY_BASE * magnitudeMultiplier);
        long expiresAt = powerSource == WardPowerSource.DURATION
            ? caster.level().getGameTime() + Math.max(20L * 5L, Math.min(20L * 60L * 12L, Math.round(20f * 45f * magnitudeMultiplier)))
            : -1L;

        MagicEnchantments existing = held.getOrDefault(DragonSpeechComponents.MAGIC_ENCHANTMENTS, MagicEnchantments.EMPTY);
        MagicEnchantment entry = type == null
            ? MagicEnchantment.newWard(wordId, powerSource, reserveMax, 1, expiresAt, caster.getUUID().toString())
            : MagicEnchantment.newTypedWard(wordId, powerSource, reserveMax, 1, type, expiresAt, caster.getUUID().toString());

        MagicEnchantments updated = existing.has(wordId) ? existing.replacing(wordId, entry) : existing.with(entry);
        held.set(DragonSpeechComponents.MAGIC_ENCHANTMENTS, updated);

        String sourceText = switch (powerSource) {
            case STAMINA_LINKED -> "bound directly to your stamina";
            case RESERVE, DURABILITY -> "filled with its own afla reserve";
            case DURATION -> "held by duration alone";
        };
        return EffectResult.success(1, (existing.has(wordId)
            ? "The ward already bound here unravels and reweaves, now "
            : "A ward binds itself into what you hold, ") + sourceText + ".");
    }

    private EffectResult applyWardDispel(ItemStack held, String wordId) {
        MagicEnchantments existing = held.getOrDefault(DragonSpeechComponents.MAGIC_ENCHANTMENTS, MagicEnchantments.EMPTY);
        var entry = existing.find(wordId);
        if (entry.isEmpty()) {
            return EffectResult.failure("What you hold carries no such ward to release.");
        }
        if (!entry.get().removable()) {
            return EffectResult.failure("This is bound into what you hold beyond any unbinding - the word finds no purchase.");
        }
        held.set(DragonSpeechComponents.MAGIC_ENCHANTMENTS, existing.without(wordId));
        return EffectResult.success(1, "The ward unwinds from what you hold and fades.");
    }

    // ============================== Blessing / Curse ==============================

    private EffectResult applyBlessingOrCurse(ItemStack held, String wordId, EnchantmentKind kind, int maxLevel, float magnitudeMultiplier) {
        MagicEnchantments existing = held.getOrDefault(DragonSpeechComponents.MAGIC_ENCHANTMENTS, MagicEnchantments.EMPTY);
        Optional<MagicEnchantment> current = existing.find(wordId);

        if (current.isPresent() && current.get().level() >= maxLevel) {
            return EffectResult.failure("This already holds all the strength it can - speaking the word again changes nothing.");
        }

        int levelsToAdd = Math.max(1, Math.round(magnitudeMultiplier));
        int newLevel = Math.min(maxLevel, current.map(MagicEnchantment::level).orElse(0) + levelsToAdd);

        MagicEnchantment entry = kind == EnchantmentKind.BLESSING
            ? MagicEnchantment.newBlessing(wordId, newLevel)
            : MagicEnchantment.newCurse(wordId, newLevel);

        MagicEnchantments updated = existing.has(wordId) ? existing.replacing(wordId, entry) : existing.with(entry);
        held.set(DragonSpeechComponents.MAGIC_ENCHANTMENTS, updated);

        return EffectResult.success(1, "The working settles into what you hold, permanent and unshakeable (level " + newLevel + " of " + maxLevel + ").");
    }

    // ============================== Hiding ==============================

    private EffectResult applyHiding(ItemStack held, Set<String> trueNames) {
        MagicEnchantments existing = held.getOrDefault(DragonSpeechComponents.MAGIC_ENCHANTMENTS, MagicEnchantments.EMPTY);

        Optional<MagicEnchantment> target = trueNames.stream()
            .filter(name -> !name.equals("duljabol"))
            .map(existing::find)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .filter(e -> !e.hidden())
            .findFirst();

        if (target.isEmpty()) {
            return EffectResult.failure("You must name a working already bound here to conceal it.");
        }

        MagicEnchantments afterHiding = existing.replacing(target.get().wordId(), target.get().withHidden(true));
        Optional<MagicEnchantment> hidingEntry = afterHiding.find("duljabol");
        int newLevel = hidingEntry.map(e -> e.level() + 1).orElse(1);
        MagicEnchantment hidingCurse = MagicEnchantment.newCurse("duljabol", newLevel);
        MagicEnchantments finalEnchantments = afterHiding.has("duljabol")
            ? afterHiding.replacing("duljabol", hidingCurse)
            : afterHiding.with(hidingCurse);

        held.set(DragonSpeechComponents.MAGIC_ENCHANTMENTS, finalEnchantments);
        return EffectResult.success(1, "What was bound here sinks out of sight - still bound, still working, just no longer seen.");
    }

    // ============================== Vanilla ==============================

    private EffectResult applyVanilla(ServerPlayer caster, ItemStack held, String wordId, float magnitudeMultiplier) {
        ResourceLocation vanillaEnchantId = VanillaEnchantWords.enchantIdFor(wordId);
        ItemCategory category = vanillaCategoryFor(wordId);

        if (held.getItem() instanceof com.dragonspeech.enchant.MagicEnchantableItem) {
            return EffectResult.failure("Jewelry channels a different kind of working - this one will not take.");
        }
        if (!category.matches(held)) {
            return EffectResult.failure("What you hold is not the kind of thing this working shapes itself to.");
        }

        var registryAccess = caster.level().registryAccess();
        var enchantRegistry = registryAccess.registryOrThrow(Registries.ENCHANTMENT);
        Holder<Enchantment> holder = enchantRegistry.getHolder(ResourceKey.create(Registries.ENCHANTMENT, vanillaEnchantId)).orElse(null);
        if (holder == null) {
            return EffectResult.failure("The working finds nothing to answer it - something is wrong with the binding itself.");
        }

        ItemEnchantments current = held.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);

        for (String conflictingWord : VANILLA_EXCLUSIVE_WITH.getOrDefault(wordId, Set.of())) {
            ResourceLocation conflictingId = VanillaEnchantWords.enchantIdFor(conflictingWord);
            if (conflictingId != null && current.keySet().stream().anyMatch(h -> h.is(conflictingId))) {
                return EffectResult.failure("What you hold already carries a working this one cannot share space with.");
            }
        }

        int currentLevel = current.getLevel(holder);
        int maxLevel = holder.value().getMaxLevel();
        if (currentLevel >= maxLevel) {
            return EffectResult.failure("This already holds the working as strongly as it can - speaking the word again changes nothing.");
        }

        int levelsToAdd = Math.max(1, Math.round(magnitudeMultiplier));
        int newLevel = Math.min(maxLevel, currentLevel + levelsToAdd);

        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(current);
        mutable.set(holder, newLevel);
        held.set(DataComponents.ENCHANTMENTS, mutable.toImmutable());

        return EffectResult.success(1, "The working takes hold, binding itself into what you carry.");
    }

    private EffectResult applyVanillaDispel(ItemStack held, String wordId) {
        ResourceLocation vanillaEnchantId = VanillaEnchantWords.enchantIdFor(wordId);
        ItemEnchantments current = held.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        if (current.keySet().stream().noneMatch(h -> h.is(vanillaEnchantId))) {
            return EffectResult.failure("What you hold carries no such working to release.");
        }
        ItemEnchantments.Mutable rebuilt = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        for (Holder<Enchantment> existingHolder : current.keySet()) {
            if (!existingHolder.is(vanillaEnchantId)) {
                rebuilt.set(existingHolder, current.getLevel(existingHolder));
            }
        }
        held.set(DataComponents.ENCHANTMENTS, rebuilt.toImmutable());
        return EffectResult.success(1, "The working unwinds from what you hold and fades.");
    }

    private static ItemCategory vanillaCategoryFor(String wordId) {
        return switch (wordId) {
            case "hvassa", "herfang", "hrinda", "eldbit" -> ItemCategory.WEAPON;
            case "hlifd" -> ItemCategory.ARMOR;
            case "leikni", "gaefa", "mjukhond" -> ItemCategory.TOOL;
            case "seigla", "laekning" -> ItemCategory.UNIVERSAL;
            default -> ItemCategory.UNIVERSAL;
        };
    }
}
