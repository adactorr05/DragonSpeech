package com.dragonspeech.growth;

import com.dragonspeech.stamina.PlayerMagicData;
import com.dragonspeech.stamina.StaminaAccess;
import com.dragonspeech.vocabulary.VocabularyAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Maximum stamina grows at vocabulary milestones rather than from pure
 * repetition - this rewards exploration/discovery over grinding the same
 * cast over and over, matching the design goal of encouraging players to
 * go find new words rather than farm one.
 */
public final class StaminaMilestones {

    private static final int WORDS_PER_MILESTONE = 5;
    private static final float STAMINA_PER_MILESTONE = 10f;

    private StaminaMilestones() {}

    public static void onWordDiscovered(ServerPlayer player) {
        // The generated Word of Words is deliberately excluded from ordinary
        // vocabulary progression. It can be forgotten/relearned across generations
        // and must never let reshuffling farm permanent max-stamina milestones.
        int knownCount = VocabularyAccess.get(player).knownWords().size();
        if (knownCount > 0 && knownCount % WORDS_PER_MILESTONE == 0) {
            PlayerMagicData magic = StaminaAccess.get(player);
            float newMax = magic.maxStamina() + STAMINA_PER_MILESTONE;
            PlayerMagicData updated = magic.withMaxStamina(newMax).withStamina(magic.stamina() + STAMINA_PER_MILESTONE);
            StaminaAccess.set(player, updated);
            player.sendSystemMessage(Component.literal("Your reserve of strength grows - you feel able to hold more."));
        }
    }
}
