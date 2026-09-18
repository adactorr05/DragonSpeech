package com.dragonspeech.dragon;

import com.dragonspeech.growth.AttunementAccess;
import com.dragonspeech.word.Domain;
import net.minecraft.server.level.ServerPlayer;

import java.util.Random;

/**
 * Extracted from DragonEggBlockItem's own (previously private)
 * isCompatible() - same exact logic, now shared so
 * DragonEggHatchingBlockEntity's own hatch-completion logic (does the
 * placing player actually bond, or does this hatch wild?) can use the
 * identical compatibility roll without duplicating/risking divergence
 * from the item's own "check the egg" interaction.
 *
 * Constants kept exactly as they were on the item - not re-tuned here.
 */
public final class EggCompatibility {

    public static final float NEVER_BONDABLE_CHANCE = 0.08f;
    public static final float BASE_COMPATIBILITY_THRESHOLD = 0.45f;
    public static final float COMPATIBILITY_PER_ATTUNEMENT = 0.003f; // +0.3% per point, capped below

    private EggCompatibility() {}

    public static boolean rollNeverBondable(long seed) {
        return new Random(seed).nextFloat() < NEVER_BONDABLE_CHANCE;
    }

    public static boolean isCompatible(ServerPlayer player, long seed) {
        Random playerRoll = new Random(seed ^ player.getUUID().getMostSignificantBits());
        float attunement = AttunementAccess.get(player).get(Domain.MIND);
        float threshold = Math.min(0.9f, BASE_COMPATIBILITY_THRESHOLD + attunement * COMPATIBILITY_PER_ATTUNEMENT);
        return playerRoll.nextFloat() < threshold;
    }
}
