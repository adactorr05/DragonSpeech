package com.dragonspeech.client.enchant;

import com.dragonspeech.enchant.EnchantmentKind;
import com.dragonspeech.enchant.MagicEnchantment;
import com.dragonspeech.enchant.MagicEnchantments;
import com.dragonspeech.enchant.WardPowerSource;
import com.dragonspeech.storage.DragonSpeechComponents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.Map;

/**
 * Appends tooltip lines for this mod's own custom ward/blessing/curse
 * enchantments (MagicEnchantments) - the piece that was always missing,
 * and specifically the thing blocking Curse of Hiding from meaning
 * anything (there was nothing here to hide FROM before this file).
 *
 * DELIBERATELY NOT a mixin: real vanilla enchantments (applied via
 * ApplyVanillaEnchantEffectHandler, using vanilla's OWN ItemEnchantments
 * component) already get a tooltip for free through vanilla's own
 * rendering - nothing needed here for those at all. This file is only
 * for MagicEnchantments, which vanilla has no idea exists.
 *
 * Uses Fabric's own ItemTooltipCallback event rather than overriding
 * appendHoverText on individual Item subclasses, specifically because
 * wards/blessings/curses can end up on ANY item (a diamond sword can
 * carry a galdrverja ward just as easily as a ring can) - an
 * item-subclass override would only ever catch MagicEnchantableItem
 * (jewelry), missing every non-jewelry item a ward gets bound into.
 *
 * VERSION-RISK NOTE: this is the one piece of this whole file with any
 * real uncertainty - ItemTooltipCallback specifically (from
 * fabric-item-api-v1) couldn't be directly confirmed as a resolved
 * dependency from within this sandbox (no build.gradle dependency list
 * was inspectable). It's bundled in the standard umbrella "fabric-api"
 * dependency this project already pulls in successfully for other
 * modules (fabric-entity-events-v1, confirmed working elsewhere this
 * session) - reasoned high confidence, not a decompiled-source-level
 * certainty. If this specific import fails to resolve, check whether
 * fabric-item-api-v1 needs to be added explicitly in build.gradle
 * (some setups split modules out rather than bundling everything).
 */
public final class MagicEnchantmentTooltips {

    private MagicEnchantmentTooltips() {}

    public static void register() {
        ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
            MagicEnchantments enchantments = stack.getOrDefault(DragonSpeechComponents.MAGIC_ENCHANTMENTS, MagicEnchantments.EMPTY);
            if (enchantments.isEmpty()) {
                return;
            }

            for (MagicEnchantment entry : enchantments.entries()) {
                if (entry.hidden()) {
                    continue; // concealed by Curse of Hiding - still fully active, just not rendered (see MagicEnchantment's own doc)
                }
                lines.add(lineFor(entry));
            }
        });
    }

    private static Component lineFor(MagicEnchantment entry) {
        String label = displayName(entry.wordId());
        // Config GUI (Client tab) "Verbose Enchant Tooltips" - compact mode drops the state suffix
        // (ward durability / blessing level), showing just the name. Curse of Hiding's own line is
        // unaffected either way - it never showed a level to begin with (see its own note below).
        boolean verbose = com.dragonspeech.client.config.DragonSpeechClientConfig.enchantTooltipVerbose();
        return switch (entry.kind()) {
            case WARD -> {
                if (!verbose) {
                    yield Component.literal("Ward: " + label).withStyle(style -> style.withColor(0xFF55CCFF));
                }
                String state = entry.powerSource() == WardPowerSource.STAMINA_LINKED
                    ? "bound to your own strength"
                    : (entry.isActive()
                        ? Math.round(entry.durabilityCurrent()) + "/" + Math.round(entry.durabilityMax())
                        : "empty");
                yield Component.literal("Ward: " + label + " (" + state + ")").withStyle(style -> style.withColor(0xFF55CCFF));
            }
            case BLESSING -> {
                String levelSuffix = verbose && entry.level() > 1 ? " " + toRoman(entry.level()) : "";
                yield Component.literal("Blessing: " + label + levelSuffix).withStyle(style -> style.withColor(0xFF55FF7A));
            }
            // "duljabol" (Curse of Hiding) ALWAYS shows - proving
            // something is concealed - but NEVER shows its own level,
            // so the viewer can't tell how many things are hidden.
            // Every other curse shows normally (curses don't otherwise
            // carry a level worth displaying - all 1 except this one).
            case CURSE -> Component.literal("Curse: " + label).withStyle(style -> style.withColor(0xFFFF5555));
        };
    }

    /** wordId -> a readable display name - the true_name strings (e.g. "vaengheill") aren't meant to double as tooltip labels. */
    private static final Map<String, String> DISPLAY_NAMES = Map.ofEntries(
        Map.entry("galdrverja", "Bound Ward"),
        Map.entry("vaengheill", "Bonded Wing"),
        Map.entry("kyrrafl", "Quiet Reserve"),
        Map.entry("merkiheill", "Clear Sign"),
        Map.entry("arinheill", "Warm Hearth"),
        Map.entry("nafnheill", "Whispered Names"),
        Map.entry("handheill", "Steady Hand"),
        Map.entry("lifheill", "Resurrection"),
        Map.entry("ennisbol", "the Marked Brow"),
        Map.entry("hungrord", "the Hungry Word"),
        Map.entry("hugopna", "the Open Mind"),
        Map.entry("thunnhula", "the Thin Veil"),
        Map.entry("grafleysa", "the Restless Grave"),
        Map.entry("visnatak", "the Withering Grasp"),
        Map.entry("haugbol", "the Grave"),
        Map.entry("duljabol", "the Concealing"),
        Map.entry("eldgaldr", "Fire Ward"),
        Map.entry("hoggaldr", "Melee Ward"),
        Map.entry("sprengaldr", "Blast Ward"),
        Map.entry("fallgaldr", "Fall Ward")
    );

    private static String displayName(String wordId) {
        return DISPLAY_NAMES.getOrDefault(wordId, capitalize(wordId));
    }

    private static String capitalize(String s) {
        if (s.isEmpty()) {
            return s;
        }
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase(Locale.ROOT);
    }

    private static String toRoman(int level) {
        return switch (level) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            default -> String.valueOf(level);
        };
    }
}
