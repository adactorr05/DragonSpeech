package com.dragonspeech.enchant;

import com.dragonspeech.accessory.AccessorySlotsAccess;
import com.dragonspeech.storage.DragonSpeechComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * The single lookup point for "what blessing/curse effects does this
 * player currently have active" - scans their 4 accessory slots fresh
 * each call (no caching, no separate player-state tracking to keep in
 * sync - the items themselves are always the source of truth, same
 * philosophy as everything else equipment-related in this mod). WARD
 * entries are skipped entirely here - those are combat-time absorption,
 * handled by MagicWardCombat, not a blessing/curse effect.
 *
 * If the SAME blessing/curse word is active on more than one equipped
 * item at once (unusual, but not prevented), the HIGHEST level among
 * them wins, not a sum - two Quiet Reserve rings don't stack their
 * regen bonus on top of each other, matching how most "how much of this
 * do I have" checks in games work by default.
 */
public final class EquippedEnchantments {

    private EquippedEnchantments() {}

    public static Map<String, Integer> scan(ServerPlayer player) {
        Map<String, Integer> levels = new HashMap<>();
        for (ItemStack stack : AccessorySlotsAccess.get(player)) {
            if (stack.isEmpty()) {
                continue;
            }
            MagicEnchantments enchantments = stack.getOrDefault(DragonSpeechComponents.MAGIC_ENCHANTMENTS, MagicEnchantments.EMPTY);
            for (MagicEnchantment e : enchantments.entries()) {
                if (e.kind() == EnchantmentKind.WARD) {
                    continue;
                }
                levels.merge(e.wordId(), e.level(), Math::max);
            }
        }
        return levels;
    }

    public static int levelOf(ServerPlayer player, String wordId) {
        return scan(player).getOrDefault(wordId, 0);
    }

    public static boolean has(ServerPlayer player, String wordId) {
        return levelOf(player, wordId) > 0;
    }
}
