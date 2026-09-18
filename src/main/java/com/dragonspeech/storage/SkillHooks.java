package com.dragonspeech.storage;

import com.dragonspeech.event.DragonSpeechEvents;
import net.minecraft.network.chat.Component;

/**
 * Ties the two storage skills to specific words. WORD_DISCOVERED's
 * callback only carries the resolved Word, not its ResourceLocation id,
 * so matching happens on trueName - safe here since we control this
 * vocabulary and true names are unique within it.
 */
public final class SkillHooks {

    private SkillHooks() {}

    public static void register() {
        DragonSpeechEvents.WORD_DISCOVERED.register((player, word, method) -> {
            if (word.trueName().equalsIgnoreCase("skynja")) {
                SkillsAccess.set(player, SkillsAccess.get(player).withSenseStamina(true));
                player.sendSystemMessage(Component.literal(
                    "You learn to feel the quiet hum of stored strength in the world around you."));
            } else if (word.trueName().equalsIgnoreCase("draga")) {
                SkillsAccess.set(player, SkillsAccess.get(player).withGatherStamina(true));
                player.sendSystemMessage(Component.literal(
                    "You learn to draw that strength into yourself."));

            // --- Phase 6: Mind duel skills. Each is a trained SKILL, not
            // a word you speak in the moment - learning hugleita, for
            // instance, does not let you "cast" contact; it flips
            // canReachOut permanently, and ContactResolver checks that
            // flag directly instead of anyone assembling a sentence.
            } else if (word.trueName().equalsIgnoreCase("hugsnert")) {
                SkillsAccess.set(player, SkillsAccess.get(player).withCanSenseMinds(true));
                player.sendSystemMessage(Component.literal(
                    "You learn to feel the faint press of nearby minds, though you cannot yet reach them."));
            } else if (word.trueName().equalsIgnoreCase("hugleita")) {
                SkillsAccess.set(player, SkillsAccess.get(player).withCanReachOut(true));
                player.sendSystemMessage(Component.literal(
                    "You learn to reach out with your mind and deliberately touch another. This is not a word - it is a skill you now carry with you always."));
            } else if (word.trueName().equalsIgnoreCase("hugvarna")) {
                SkillsAccess.set(player, SkillsAccess.get(player).withCanWallMind(true));
                player.sendSystemMessage(Component.literal(
                    "You learn to wall your own mind against intrusion. Your fortitude in any mind duel grows."));
            } else if (word.trueName().equalsIgnoreCase("hugrista")) {
                SkillsAccess.set(player, SkillsAccess.get(player).withCanReadThoughts(true));
                player.sendSystemMessage(Component.literal(
                    "You learn to read the surface of a thought once you have breached a mind."));
            } else if (word.trueName().equalsIgnoreCase("hugbinda")) {
                SkillsAccess.set(player, SkillsAccess.get(player).withCanBindTotally(true));
                player.sendSystemMessage(Component.literal(
                    "You learn to bind mind to mind with total precision - true name domination is now within your reach."));

            // --- Enchantment skill: "gala" (general) grants it outright,
            // but so does learning ANY specific enchant word independently -
            // these are two separate, equally valid discovery paths, not a
            // prerequisite chain. Every specific enchant word added in
            // later phases needs its own check added here alongside gala's.
            } else if (word.trueName().equalsIgnoreCase("gala")) {
                SkillsAccess.set(player, SkillsAccess.get(player).withCanEnchant(true));
                player.sendSystemMessage(Component.literal(
                    "You learn the shape of binding a working into an object that will hold it - the words for which working, and how, still need finding."));
            } else if (word.trueName().equalsIgnoreCase("galdrverja")) {
                SkillsAccess.set(player, SkillsAccess.get(player).withCanEnchant(true));
                player.sendSystemMessage(Component.literal(
                    "You learn to bind a ward into an object, whether or not you ever learn the general word for enchanting at all."));
            } else if (word.trueName().equalsIgnoreCase("duljabol")) {
                SkillsAccess.set(player, SkillsAccess.get(player).withCanEnchant(true));
                player.sendSystemMessage(Component.literal(
                    "You learn to hide a working already bound into an object - it will go on working, unseen, once you do."));
            } else if (isTypedWardWord(word.trueName())) {
                SkillsAccess.set(player, SkillsAccess.get(player).withCanEnchant(true));
                player.sendSystemMessage(Component.literal(
                    "You learn to bind this shape of ward into an object, precise to a single kind of harm."));
            } else if (isBlessingOrCurseWord(word.trueName())) {
                SkillsAccess.set(player, SkillsAccess.get(player).withCanEnchant(true));
                player.sendSystemMessage(Component.literal(
                    "You learn to bind this working into an object permanently - whatever it does, once done, cannot be undone."));
            } else if (isVanillaEnchantWord(word.trueName())) {
                SkillsAccess.set(player, SkillsAccess.get(player).withCanEnchant(true));
                player.sendSystemMessage(Component.literal(
                    "You learn to bind this working into an object - a working that can, if need be, still be unbound later."));
            }
        });
    }

    /** The 10 vanilla-enchantment words - see VanillaEnchantWords for the full wordId->enchantment table these were built from. */
    private static boolean isVanillaEnchantWord(String trueName) {
        return com.dragonspeech.enchant.VanillaEnchantWords.enchantIdFor(trueName.toLowerCase()) != null;
    }

    /** The 4 damage-type-specific ward words - galdrverja (generic) is handled separately above since it existed first and has its own message. */
    private static boolean isTypedWardWord(String trueName) {
        return switch (trueName.toLowerCase()) {
            case "eldgaldr", "hoggaldr", "sprengaldr", "fallgaldr" -> true;
            default -> false;
        };
    }

    /** The 14 blessing/curse words - see ApplyBlessingOrCurseEffectHandler and EffectHandlerRegistry for where these actually get registered. Each one independently grants canEnchant, same as galdrverja and gala do, matching "learn gala OR learn the specific word" being two equally valid paths. */
    private static boolean isBlessingOrCurseWord(String trueName) {
        return switch (trueName.toLowerCase()) {
            case "vaengheill", "kyrrafl", "merkiheill", "arinheill", "nafnheill", "handheill", "lifheill",
                 "ennisbol", "hungrord", "hugopna", "thunnhula", "grafleysa", "visnatak", "haugbol" -> true;
            default -> false;
        };
    }
}
