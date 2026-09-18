package com.dragonspeech.effect;

import com.dragonspeech.DragonSpeech;
import com.dragonspeech.mind.MindControlService;
import com.dragonspeech.mind.MindFortitudeService;
import com.dragonspeech.mind.PossessionService;
import com.dragonspeech.word.Word;
import com.dragonspeech.word.WordCategory;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.player.Player;

import java.util.Random;
import java.util.Set;

/**
 * hambinda - "to bind oneself into another's skin." The EBW-Possession
 * working: mobs only, one skin at a time. This deliberately complements
 * (never replaces) the hugbinda -> mind-duel -> Control path:
 *
 *   Control (existing):  remote puppeteering; works on PLAYERS and mobs;
 *                        earned through a full mind duel; your body stays
 *                        behind, exposed.
 *   Possession (this):   incarnation; MOBS ONLY; a single resistance roll
 *                        against the creature's mind-fortitude at cast
 *                        time; you go to the creature and wear it.
 *
 * A player's skin can never be taken this way - a will as strong as a
 * player's must be fought properly (the duel), not slipped into. Boss
 * shapes (dragon, wither) are too vast to wear. A failed binding leaves
 * the creature aware of you and angry, which is the cost of reaching
 * into a mind and losing your grip.
 *
 * Duration scales with the verb's precision and power modifiers
 * (mikla/ofsa lengthen the hold), capped hard - sneaking sheds the skin
 * early, exactly as in EBW.
 */
public class PossessEffectHandler implements EffectHandler {

    private static final Random RANDOM = new Random();

    private static final int BASE_DURATION_TICKS = 20 * 30;   // 30s
    private static final int MAX_DURATION_TICKS = 20 * 120;   // 2min hard cap

    private static final EffectHandlerCaps CAPS = new EffectHandlerCaps(
        1, 10.0f, 12f, Set.of(TargetKind.ENTITY)
    );

    @Override
    public ResourceLocation id() {
        return DragonSpeech.id("possess");
    }

    @Override
    public EffectHandlerCaps caps() {
        return CAPS;
    }

    @Override
    public float estimateBaseMagnitude(EffectInvocation invocation) {
        // Wearing another creature's whole body is among the heaviest
        // single-target workings in the mod - priced accordingly.
        return 10f;
    }

    @Override
    public EffectResult apply(EffectInvocation invocation) {
        var capViolation = CAPS.validateTargets(invocation);
        if (capViolation.isPresent()) {
            return EffectResult.failure(capViolation.get());
        }

        ServerPlayer caster = invocation.caster();

        if (PossessionService.isPossessing(caster)) {
            return EffectResult.failure("You already wear a skin that is not yours. Shed it first.");
        }

        if (!(invocation.targets().get(0) instanceof EffectTarget.OfEntity(Entity raw))) {
            return EffectResult.failure("The skin-binding needs a living mark.");
        }

        if (raw instanceof Player) {
            return EffectResult.failure(
                "A player's skin cannot simply be taken - a will like that must be fought. Bind their mind (hugbinda) and win the duel.");
        }
        if (!(raw instanceof Mob target) || !target.isAlive()) {
            return EffectResult.failure("There is no living skin there to wear.");
        }
        if (target instanceof EnderDragon || target instanceof WitherBoss) {
            return EffectResult.failure("That shape is too vast - your self would drown in it.");
        }
        if (PossessionService.isPossessed(target.getUUID()) || MindControlService.isControlled(target.getUUID())) {
            return EffectResult.failure("Another will already holds that body.");
        }

        // One resistance roll against the creature's mind-fortitude - the
        // same number the duel system uses, so a Warden is as hard to wear
        // as it is to dominate, and a chicken is neither.
        float resistChance = Math.min(85f, Math.max(5f, MindFortitudeService.fortitude(target)));
        if (RANDOM.nextFloat() * 100f < resistChance) {
            target.setTarget(caster);
            return EffectResult.failure("The creature's mind bucks like a wild thing, throws your grip - and turns on you.");
        }

        float verbPrecision = invocation.composition().wordsOf(WordCategory.VERB).stream()
            .findFirst().map(Word::precision).orElse(0.5f);
        int duration = Math.min(MAX_DURATION_TICKS, Math.round(
            BASE_DURATION_TICKS * (0.5f + verbPrecision) * (1f + Math.max(0f, invocation.modifierMagnitudeSum()))));

        if (!PossessionService.start(caster, target, duration)) {
            return EffectResult.failure("The binding slips - the skin will not open to you now.");
        }

        return EffectResult.success(1,
            "The word closes around you both, and the creature's skin becomes yours to wear. Sneak to shed it.");
    }
}
