package com.dragonspeech.effect;

import com.dragonspeech.DragonSpeech;
import com.dragonspeech.fx.DragonSpeechParticles;
import com.dragonspeech.fx.SpellFx;
import com.dragonspeech.stamina.LifeForceDrain;
import com.dragonspeech.stamina.PlayerMagicData;
import com.dragonspeech.stamina.StaminaAccess;
import com.dragonspeech.storage.ItemStaminaStorage;
import com.dragonspeech.storage.StorageMediumRegistry;
import com.dragonspeech.word.Word;
import com.dragonspeech.word.WordCategory;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

/**
 * Backs "aflsuga" - tears strength out of a living target. Every target
 * gets a real WEAKNESS/MOVEMENT_SLOWDOWN debuff (their strength is
 * genuinely diminished, whether or not they track stamina the way a
 * caster does); the actual energy drawn out now comes from
 * LifeForceDrain's shared cascade - stamina first (real for a player,
 * a persistent notional reserve otherwise - see MobStaminaAccess),
 * THEN hunger (players only), and only once BOTH are exhausted does it
 * reach for real health damage, at a deliberately poor rate.
 *
 * FIXED: this used to treat every target as freshly full EVERY cast (a
 * mob's "stamina" was entirely notional and recomputed from scratch each
 * time, never actually spent), so repeated casting on the same target
 * had no real limit at all - "no limit to how much it draws." Now
 * MobStaminaAccess gives non-player targets a real, persistent,
 * slowly-regenerating reserve, so repeated draining actually exhausts
 * something, and further draining past that point spills into real
 * (but deliberately small) health damage instead of just... continuing
 * to work at full strength forever.
 *
 * "aflflyta" (paired modifier word): the drained amount is funneled into
 * the CASTER'S OWN STAMINA instead of just dissipating. Without it, the
 * drain is pure sabotage - the target is weakened, but the caster gains
 * nothing from it.
 *
 * "afla" (paired noun, NEW): funneled into whatever the caster is
 * HOLDING instead - the same transfer draga/ChargeItemEffectHandler
 * uses, just sourced from a drained target instead of the caster's own
 * reserves. "afla" is checked FIRST; if the item can't take all of it
 * (not a valid medium, or already full), only "aflflyta" (if ALSO
 * spoken) catches the leftover into the caster's stamina - otherwise
 * whatever the item couldn't hold just dissipates, same as an
 * unclaimed drain always has.
 *
 * Deliberately does NOT read "sjalfan" for either of these - sjalfan
 * means "target = the caster," which would mean draining yourself, not
 * "and give it to myself/my held item." afla/aflflyta are the actual
 * words for that.
 */
public class DrainStaminaEffectHandler implements EffectHandler {

    private static final EffectHandlerCaps CAPS = new EffectHandlerCaps(
        1, 10.0f, 16f, Set.of(TargetKind.ENTITY)
    );

    /** How much energy a cast REQUESTS from the target, before LifeForceDrain caps it to whatever the target actually has. This is the "ask," not a guarantee - see the class doc for why that used to be the whole bug. */
    private static final float REQUESTED_ENERGY_PER_POWER = 1.5f;

    @Override
    public ResourceLocation id() {
        return DragonSpeech.id("drain_stamina");
    }

    @Override
    public EffectHandlerCaps caps() {
        return CAPS;
    }

    @Override
    public float estimateBaseMagnitude(EffectInvocation invocation) {
        return 6f;
    }

    @Override
    public EffectResult apply(EffectInvocation invocation) {
        var capViolation = CAPS.validateTargets(invocation);
        if (capViolation.isPresent()) {
            return EffectResult.failure(capViolation.get());
        }

        ServerPlayer caster = invocation.caster();
        if (!(caster.level() instanceof ServerLevel level)) {
            return EffectResult.failure("The working slips away.");
        }
        if (invocation.targets().isEmpty()
            || !(invocation.targets().get(0) instanceof EffectTarget.OfEntity(Entity raw))
            || !(raw instanceof LivingEntity target)) {
            return EffectResult.failure("There is no strength there to draw out.");
        }
        if (target == caster) {
            return EffectResult.failure("You cannot tear your own strength from yourself this way.");
        }

        float verbPrecision = invocation.composition().wordsOf(WordCategory.VERB).stream()
            .findFirst().map(Word::precision).orElse(0.5f);
        float power = 3f + verbPrecision * 4f + invocation.modifierMagnitudeSum() * 3f;
        power = Math.max(1f, Math.min(power, CAPS.maxMagnitudePerTarget()));

        int amplifier = Math.max(0, Math.min(3, Math.round(power / 4f)));
        int duration = Math.round(20 * (6 + power));

        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, duration, amplifier));
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, duration, amplifier));

        float requestedEnergy = power * REQUESTED_ENERGY_PER_POWER;
        LifeForceDrain.Outcome outcome = LifeForceDrain.drain(target, requestedEnergy, level);
        float drained = outcome.energyGathered();

        boolean toItem = invocation.composition().words().stream().anyMatch(w -> "afla".equals(w.trueName()));
        boolean flyta = invocation.composition().words().stream().anyMatch(w -> "aflflyta".equals(w.trueName()));

        Vec3 targetPos = target.position().add(0, target.getBbHeight() * 0.5, 0);
        Vec3 casterPos = caster.position().add(0, caster.getBbHeight() * 0.5, 0);

        float creditedToItem = 0f;
        float creditedToSelf = 0f;

        if (toItem && drained > 0f) {
            ItemStack held = caster.getMainHandItem();
            if (!held.isEmpty() && StorageMediumRegistry.isValidMedium(held.getItem())) {
                creditedToItem = ItemStaminaStorage.charge(held, drained);
            }
        }

        float leftoverAfterItem = drained - creditedToItem;
        if (flyta && leftoverAfterItem > 0f) {
            PlayerMagicData casterData = StaminaAccess.get(caster);
            StaminaAccess.set(caster, casterData.withStamina(casterData.stamina() + leftoverAfterItem));
            creditedToSelf = leftoverAfterItem;
        }

        if (creditedToItem > 0f || creditedToSelf > 0f) {
            SpellFx.trail(level, DragonSpeechParticles.DUST, 0x8a6a3b, 0x2a2050, targetPos, casterPos, 0.4);
            SpellFx.spiral(level, DragonSpeechParticles.SPARKLE, 0x8a6a3b, 0xffffff, caster, 0.6);
        }
        SpellFx.burst(level, DragonSpeechParticles.DUST, 0x8a6a3b, 0x2a2050, targetPos, 10, 0.08);

        String message;
        if (drained <= 0.01f) {
            message = "There is nothing left in them to tear free - they are spent, and give up nothing more.";
        } else if (outcome.healthDamageDealt() > 0f) {
            message = (creditedToItem > 0f || creditedToSelf > 0f)
                ? "Their strength is spent - what's left tears free from life itself, and rushes into you."
                : "Their strength is spent - what's left tears free from life itself, and scatters, spent for nothing.";
        } else if (creditedToItem > 0f) {
            message = "Their strength tears free and pours into what you hold.";
        } else if (creditedToSelf > 0f) {
            message = "Their strength tears free and rushes into you.";
        } else {
            message = "Their strength tears free and scatters, spent for nothing.";
        }

        return EffectResult.success(1, message);
    }
}
