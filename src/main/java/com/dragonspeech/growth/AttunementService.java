package com.dragonspeech.growth;

import com.dragonspeech.word.Domain;
import net.minecraft.server.level.ServerPlayer;

/**
 * Turns raw attunement into the discount multiplier SpellCostCalculator
 * expects, and grants attunement XP when a player casts in a domain.
 * Every number here is a balance knob - tune freely as you playtest.
 */
public final class AttunementService {

    /** At max attunement (100), a domain's spells cost this fraction less. 0.5 = half price. */
    private static final float MAX_DISCOUNT = 0.5f;

    /** Attunement XP granted per successful cast in a domain. */
    private static final float XP_PER_CAST = 0.5f;

    private AttunementService() {}

    /** Feed straight into SpellCastResolver.prepare() as the domainAttunementMultiplier argument. 1.0 = no discount, down to (1 - MAX_DISCOUNT) at full attunement. */
    public static float costMultiplier(ServerPlayer player, Domain domain) {
        float attunement = AttunementAccess.get(player).get(domain);
        float fraction = attunement / PlayerAttunementData.MAX_ATTUNEMENT;
        return 1.0f - (fraction * MAX_DISCOUNT);
    }

    public static void grantExperience(ServerPlayer player, Domain domain) {
        PlayerAttunementData data = AttunementAccess.get(player);
        boolean human = com.dragonspeech.race.RaceAccess.get(player).race()
            .map(race -> race == com.dragonspeech.race.RaceType.HUMAN).orElse(false);
        float xp = human ? XP_PER_CAST * 1.5f : XP_PER_CAST;
        AttunementAccess.set(player, data.withIncreased(domain, xp));
        com.dragonspeech.mind.PersonalityShiftService.checkForShift(player);
    }
}
