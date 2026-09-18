package com.dragonspeech.mob.casting;

import com.dragonspeech.item.DragonSpeechItems;
import com.dragonspeech.storage.DragonSpeechComponents;
import com.dragonspeech.vocabulary.VocabularyService;
import com.dragonspeech.word.RiskTier;
import com.dragonspeech.word.Word;
import com.dragonspeech.word.WordCategory;
import com.dragonspeech.word.WordRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

/**
 * Turns a SpellcastingMob's own known-word vocabulary into a live
 * MerchantOffers list: "elves and Humans will sometimes have trades to
 * trade for different words" per the design brief. A mob offers to teach
 * exactly the words it knows (same pool it fights with - see
 * MobWordPools), minus a few things that would make poor trade goods:
 *
 * - Pure grammar glue (WordCategory.CONTROL, plus the handful of
 *   near-meaningless connector particles like "ok"/"med"/"til"/"an") -
 *   nobody should spend emeralds on "and".
 * - RiskTier.CATASTROPHIC words from a mob's ordinary combat vocabulary -
 *   these stay off the normal trade list entirely; the "elven trial"
 *   discovery path into them is now TABLETS (see tabletOffersFor below),
 *   not a rare word-scroll offer like the first version of this class.
 * - Anything the current trading player already knows - there is nothing
 *   left to sell them.
 *
 * TWO KINDS OF OFFER now, per explicit follow-up direction:
 *
 * 1. "UNKNOWN WORD" PAPER - every normal vocabulary-derived offer. Used
 *    to be a scroll with the true word name stamped right on it
 *    ("Scroll of \"kaldna\""), which spoiled the purchase before you'd
 *    even bought it. buildScrollOffer() below no longer sets a spoiler
 *    display name - the item looks like plain unidentified paper (see
 *    WordScrollItem's tooltip) and only reveals which word it is once
 *    actually read. A domain hint ("mind, element, force, etc.") is
 *    still shown so a buyer has SOME idea what they're getting.
 *
 * 2. TABLETS - the rare, precious end of trading, replacing the old
 *    "rarely offers a CATASTROPHIC word scroll" mechanic entirely. Elves
 *    and Human Mages only ever offer tier-1 (Worn) tablets, and rarely;
 *    Elder Elves can also offer tier-2 (Ancient) and tier-3 (Primordial)
 *    tablets, still rare but at meaningfully better odds than the other
 *    two. A tablet doesn't teach one fixed word - see WordTabletItem/
 *    TabletContentGenerator - it's a translation minigame with 1-3 real
 *    words hidden inside, which fits "buying something valuable and
 *    uncertain" far better than a guaranteed-word purchase did, and
 *    reuses a whole existing, already-built system this had no
 *    connection to before. Which tier(s) a given individual mob happens
 *    to offer is decided ONCE, deterministically, from that mob's own
 *    UUID (see tabletOffersFor) - not re-rolled every getOffers() call,
 *    for the same "closing and reopening shouldn't reroll rare luck"
 *    reasoning the old trial-word system already established.
 *
 * Deliberately stateless (beyond that per-mob-UUID-seeded tablet roll)
 * and rebuilt on every getOffers() call (see SpellcastingMobEntity.
 * getOffers) rather than cached/persisted - see that method's own
 * comment for why that's simpler and safer than the usual
 * persisted-offer-list approach.
 */
public final class MobTradeOffers {

    private MobTradeOffers() {}

    private static final Set<String> UNTRADEABLE_PARTICLES = Set.of("ok", "med", "til", "an");

    /** "There are also a lot of trades. Lower the amount of trades shown" per direction - was every single eligible word at once (Elder Elf regularly hit 40+). */
    private static final int MAX_SCROLL_OFFERS = 8;

    public static MerchantOffers build(SpellcastingMob merchant, Player tradingPlayer) {
        MerchantOffers offers = new MerchantOffers();

        List<ResourceLocation> eligible = new ArrayList<>();
        for (ResourceLocation id : merchant.vocabulary().words()) {
            Word word = WordRegistry.get(id);
            if (word == null || !isTradeable(word) || alreadyKnown(tradingPlayer, id)) {
                continue;
            }
            eligible.add(id);
        }

        // Stable per (mob, player) pair - reopening the trade screen
        // shows the SAME subset rather than reshuffling every click, but
        // a different player (or the same player after this mob's
        // vocabulary changes) can see a different one.
        long seed = merchant.asEntity().getUUID().getMostSignificantBits()
            ^ (tradingPlayer.getUUID().getLeastSignificantBits());
        Random selectionRandom = new Random(seed);
        Collections.shuffle(eligible, selectionRandom);

        for (ResourceLocation id : eligible.stream().limit(MAX_SCROLL_OFFERS).toList()) {
            Word word = WordRegistry.get(id);
            offers.add(buildScrollOffer(id, word));
        }

        offers.addAll(tabletOffersFor(merchant));

        return offers;
    }

    /**
     * Breaks down EXACTLY where a mob's vocabulary went when getOffers()
     * comes back empty - "knows N total, M survive the trade filters
     * (grammar/CATASTROPHIC excluded), K of those you already know" -
     * shown directly in the "has nothing left to teach you" chat message
     * itself rather than requiring a server log lookup.
     */
    public static String diagnose(SpellcastingMob merchant, Player tradingPlayer) {
        int total = merchant.vocabulary().words().size();
        int tradeable = 0;
        int knownByPlayer = 0;
        for (ResourceLocation id : merchant.vocabulary().words()) {
            Word word = WordRegistry.get(id);
            if (word == null || !isTradeable(word)) {
                continue;
            }
            tradeable++;
            if (alreadyKnown(tradingPlayer, id)) {
                knownByPlayer++;
            }
        }
        return "knows " + total + " word(s) total, " + tradeable + " survive the trade filters, "
            + knownByPlayer + " of those you already know";
    }

    private static boolean alreadyKnown(Player tradingPlayer, ResourceLocation id) {
        return tradingPlayer instanceof ServerPlayer serverPlayer && VocabularyService.knowsWord(serverPlayer, id);
    }

    private static boolean isTradeable(Word word) {
        if (word.category() == WordCategory.CONTROL) {
            return false;
        }
        if (UNTRADEABLE_PARTICLES.contains(word.trueName())) {
            return false;
        }
        return word.riskTier() != RiskTier.CATASTROPHIC;
    }

    /**
     * Tier-1 (Worn) tablets for Elf and Human Mage (Human Mage at lower
     * odds - "less rare words, and tier 1 tablets" per direction, kept
     * beneath a plain Elf's own odds to match the established power
     * hierarchy); Elder Elf gets a shot at all three tiers, 2 and 3
     * still meaningfully rarer than 1. Shade doesn't trade at all
     * (CATASTROPHIC tier falls through to the empty list below).
     */
    private static List<MerchantOffer> tabletOffersFor(SpellcastingMob merchant) {
        List<MerchantOffer> result = new ArrayList<>();
        Random perMobRandom = new Random(merchant.asEntity().getUUID().getMostSignificantBits());

        switch (merchant.powerTier()) {
            case ADEPT -> addTabletChance(result, perMobRandom, 0.20f, 1);          // Elf
            case ELDER -> {                                                        // Elder Elf
                addTabletChance(result, perMobRandom, 0.35f, 1);
                addTabletChance(result, perMobRandom, 0.12f, 2);
                addTabletChance(result, perMobRandom, 0.04f, 3);
            }
            case APPRENTICE -> addTabletChance(result, perMobRandom, 0.08f, 1);     // Human Mage
            default -> { /* Shade (CATASTROPHIC) doesn't trade */ }
        }
        return result;
    }

    private static void addTabletChance(List<MerchantOffer> result, Random random, float chance, int tier) {
        if (random.nextFloat() >= chance) {
            return;
        }
        Item tabletItem = switch (tier) {
            case 2 -> DragonSpeechItems.WORD_TABLET_ANCIENT;
            case 3 -> DragonSpeechItems.WORD_TABLET_PRIMORDIAL;
            default -> DragonSpeechItems.WORD_TABLET_WORN;
        };
        ItemCost cost = new ItemCost(Items.EMERALD, tabletPriceFor(tier));
        result.add(new MerchantOffer(
            cost,
            Optional.empty(),
            new ItemStack(tabletItem),
            1,                 // maxUses - a mob's rare tablet luck is a one-time thing, same as the old trial words were
            10 + tier * 5,     // xp - rarer tier, bigger moment
            0.05f
        ));
    }

    private static int tabletPriceFor(int tier) {
        return switch (tier) {
            case 2 -> 30;
            case 3 -> 55;
            default -> 16;
        };
    }

    /**
     * VERSION-RISK NOTE: MerchantOffer/ItemCost are the newest, most
     * churned part of the vanilla trading API (the 1.20.5 trade rework
     * replaced raw ItemStack costs with ItemCost). The 6-argument
     * MerchantOffer(ItemCost, Optional&lt;ItemCost&gt;, ItemStack, int maxUses,
     * int xp, float priceMultiplier) constructor and the ItemCost(ItemLike,
     * int) constructor are both written from memory of that API shape,
     * without a decompiled jar to confirm against this session - if this
     * file doesn't compile, check both constructors' exact overloads
     * first; everything else in this class is ordinary word/registry code.
     */
    private static MerchantOffer buildScrollOffer(ResourceLocation wordId, Word word) {
        net.minecraft.world.item.Item currency = currencyFor(word);
        ItemCost cost = new ItemCost(currency, priceFor(word, currency));
        ItemStack result = new ItemStack(DragonSpeechItems.WORD_SCROLL);
        result.set(DragonSpeechComponents.TAUGHT_WORD, wordId.toString());
        // Category hint only - NOT the true name. See WordScrollItem's
        // tooltip and this class's own doc for why the spoiler display
        // name from the first version of this method is gone.
        result.set(DragonSpeechComponents.WORD_CATEGORY_HINT, word.domain().name());

        return new MerchantOffer(
            cost,
            Optional.empty(),
            result,
            1,      // maxUses - see class comment: a word need only ever be bought once, and offers aren't persisted/restocked
            5,      // xp
            0.05f   // priceMultiplier - low; word prices are already hand-tuned per riskTier/precision below, not meant to inflate with demand
        );
    }

    /** "Currently all the trades are for emerald... there will need to be some variety" per direction - tied to the word's own value rather than arbitrary, so a buyer can learn to read the currency as a rough value signal. */
    private static net.minecraft.world.item.Item currencyFor(Word word) {
        return switch (word.riskTier()) {
            case TRIVIAL, MODERATE -> Items.EMERALD;
            case SEVERE -> word.precision() >= 0.85f ? Items.DIAMOND : Items.GOLD_INGOT;
            case CATASTROPHIC -> Items.DIAMOND; // not actually reachable here - isTradeable() excludes these - kept for completeness
        };
    }

    /** Pure balance knob, not lore. Quantity is scaled DOWN for higher-value currencies (see currencyFor) - the same raw number of diamonds as emeralds would wildly overcharge. */
    private static int priceFor(Word word, net.minecraft.world.item.Item currency) {
        int base = switch (word.riskTier()) {
            case TRIVIAL -> 2;
            case MODERATE -> 5;
            case SEVERE -> 10;
            case CATASTROPHIC -> 40; // no longer actually reachable here - CATASTROPHIC words are excluded by isTradeable() - kept for completeness/safety
        };
        int emeraldEquivalent = Math.max(1, Math.round(base * (0.6f + word.precision())));
        if (currency == Items.DIAMOND) {
            return Math.max(1, Math.round(emeraldEquivalent / 4f));
        }
        if (currency == Items.GOLD_INGOT) {
            return Math.max(1, Math.round(emeraldEquivalent * 1.5f));
        }
        return emeraldEquivalent;
    }
}
