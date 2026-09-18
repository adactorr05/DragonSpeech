package com.dragonspeech.enchant;

/**
 * Only meaningful for WARD-kind enchantments - chosen by which word is
 * spoken alongside the ward-application word, the same "the word you
 * add changes how it pays for itself" pattern aflbinda already
 * established for barriers.
 *
 *   DURABILITY     - a fixed pool, displayed via the item's real
 *                     vanilla durability bar (recolored to match the
 *                     stamina bar). Hits 0 -> the ward goes inactive
 *                     and the bar shows empty. The ITEM NEVER BREAKS -
 *                     see MagicEnchantment's own doc for how that's
 *                     enforced. Refilled the same way any other item
 *                     gets charged with stored stamina (draga-shaped),
 *                     just tagged to this specific ward.
 *   STAMINA_LINKED  - drains the WEARER directly (stamina, then hunger,
 *                     then health down to DragonSpeechConfig's current
 *                     floor) instead of anything stored in the item at
 *                     all. Deactivates once the wearer is fully tapped
 *                     out, reactivating once they recover - EXCEPT on
 *                     Magic Difficulty HARD, where there's no floor and
 *                     it can genuinely kill the wearer instead, same as
 *                     an aflbinda-linked barrier already does. Only
 *                     active while the item sits in an accessory slot -
 *                     take it off (or unequip it) and the ward simply
 *                     doesn't apply, full stop, no draining either way.
 */
public enum WardPowerSource {
    DURABILITY,
    STAMINA_LINKED
}
