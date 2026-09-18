package com.dragonspeech.effect;

import com.dragonspeech.DragonSpeech;
import com.dragonspeech.death.RecentDeaths;
import com.dragonspeech.ward.WardService;
import com.dragonspeech.ward.WardType;
import com.dragonspeech.word.Word;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.Optional;
import java.util.Set;

/**
 * Backs "aftrlifga" - the crossing reversed. Three distinct shapes,
 * chosen by whether "sjalfan" (self) is spoken and whether the caster is
 * alive or dead when they speak it:
 *
 * 1. "aftrlifga sjalfan" spoken ALIVE -> places a REVIVAL ward: a binding
 *    paid for in full now, on the chance you'll need it later. Consumed
 *    automatically by DeathHooks the instant its caster dies - see there.
 *    This is the "cast like a ward, beforehand" form.
 *
 * 2. "aftrlifga sjalfan" spoken ALIVE, with NO binding word in the
 *    sentence -> primes a short 5-second self-revival window
 *    (SelfRevivalPrimedMarkers). If death comes within it, DeathHooks
 *    catches it automatically - nothing further needs to be spoken. If
 *    not, the priming simply expires with no lasting effect. Timing
 *    substitutes for the ward-form's up-front premium.
 *
 * 3. "aftrlifga [nar/thetta]" (no sjalfan), spoken ALIVE -> the original
 *    other-caster form: reach for the most recent thread near the
 *    caster, player or MOB. Mob cost scales with the mob's size - a
 *    chicken's thread is easy to catch, a dragon's or iron golem's is a
 *    different order of working entirely (see KineticCost.massFactor,
 *    recorded at the moment of death in RecentDeaths.MobDeathRecord).
 *
 * COST for forms 1 and 3 is deliberately at the edge of what the mod
 * allows - see the constants below and the class-level notes in earlier
 * versions of this file. Form 2 is priced separately since it can't be
 * discounted by precision/attunement the normal way (see its own method).
 *
 * THE SCAR always applies, regardless of form or who is doing the
 * reviving: 1-3 known words sever from whoever is pulled back.
 */
public class ResurrectEffectHandler implements EffectHandler {

    /** Public so SummonEffectHandler can price itself as a direct fraction of this, instead of a second hardcoded number that could silently drift out of the intended ratio. */
    public static final float BASE_MAGNITUDE_OTHER = 800f;
    private static final float BASE_MAGNITUDE_WARD_FORM = 950f; // a guarantee paid in full, whether or not you ever need it
    private static final float TIMING_FORM_COST = 550f; // flat - timing substitutes for the ward-form's premium

    private static final double REACH_BLOCKS = 8.0;

    private static final EffectHandlerCaps CAPS = new EffectHandlerCaps(
        1, BASE_MAGNITUDE_WARD_FORM, (float) REACH_BLOCKS, Set.of(TargetKind.ENTITY)
    );

    @Override
    public ResourceLocation id() {
        return DragonSpeech.id("resurrect");
    }

    @Override
    public EffectHandlerCaps caps() {
        return CAPS;
    }

    @Override
    public boolean selfTargeting() {
        return true; // the caster anchors the working, whichever form it takes
    }

    @Override
    public float estimateBaseMagnitude(EffectInvocation invocation) {
        if (isSelfSpoken(invocation)) {
            float base = hasBindingWord(invocation) ? BASE_MAGNITUDE_WARD_FORM : TIMING_FORM_COST;
            // Blessing of Resurrection ("lifheill") - "far less" per spec; a flat 40% off, worn only. Single level, so no scaling needed.
            if (com.dragonspeech.enchant.EquippedEnchantments.has(invocation.caster(), "lifheill")) {
                base *= 0.6f;
            }
            return base;
        }
        Optional<Float> mobMass = peekMobMass(invocation);
        return mobMass.map(mass -> BASE_MAGNITUDE_OTHER * mass).orElse(BASE_MAGNITUDE_OTHER);
    }

    @Override
    public EffectResult apply(EffectInvocation invocation) {
        ServerPlayer caster = invocation.caster();

        if (isSelfSpoken(invocation)) {
            if (hasBindingWord(invocation)) {
                // The ward-form: a binding word alongside "aftrlifga sjalfan"
                // places a persistent REVIVAL ward - lasts until death
                // consumes it (see DeathHooks) or it's dispelled like any
                // other ward (letta + the same binding word).
                WardService.place(caster, WardType.REVIVAL, 1f, 1, true, false);
                return EffectResult.success(1, "A binding takes hold - should the crossing come for you, this word will answer first.");
            }

            // The timing-form: "aftrlifga sjalfan" alone primes a short
            // 5-second window. If death comes within it, DeathHooks
            // catches it automatically - nothing further needs to be
            // spoken, dead or alive.
            com.dragonspeech.death.SelfRevivalPrimedMarkers.prime(caster.getUUID(), caster.level().getGameTime());
            return EffectResult.success(1, "For a handful of breaths, the crossing cannot quite take you unanswered.");
        }

        // Form 3: reach for the nearest thread - player or mob, whichever is closer/more recent.
        MinecraftServer server = caster.getServer();
        if (server == null) {
            return EffectResult.failure("The working slips away.");
        }

        RecentDeaths.DeathRecord playerThread = RecentDeaths.findNear(
            caster.position(), caster.level().dimension(), REACH_BLOCKS, caster.level().getGameTime());
        RecentDeaths.MobDeathRecord mobThread = RecentDeaths.findMobNear(
            caster.position(), caster.level().dimension(), REACH_BLOCKS, caster.level().getGameTime());

        if (playerThread == null && mobThread == null) {
            return EffectResult.failure("No thread lingers here to catch - the dead are beyond this word's reach.");
        }

        // Prefer whichever thread is more recent if both are in reach.
        boolean useMob = mobThread != null && (playerThread == null || mobThread.gameTime() > playerThread.gameTime());

        if (useMob) {
            return reviveMob(caster, server, mobThread);
        }
        return revivePlayer(caster, server, playerThread);
    }

    private static EffectResult revivePlayer(ServerPlayer caster, MinecraftServer server, RecentDeaths.DeathRecord thread) {
        ServerPlayer departed = server.getPlayerList().getPlayer(thread.playerId());
        if (departed == null) {
            RecentDeaths.clear(thread.playerId());
            return EffectResult.failure("The thread is cut - they have gone where the word cannot follow.");
        }

        // Curse of the Restless Grave ("grafleysa") - "can never be
        // called back... while worn." SCOPE NOTE: this checks the
        // departed player's CURRENT accessory slots, not what they were
        // wearing at the exact moment of death - for a plain grafleysa
        // item that isn't ALSO haugbol-bound, AccessoryDeathDrops will
        // usually have already dropped it into the world before anyone
        // gets the chance to attempt a revival, meaning this check can
        // miss that case. It reliably catches the (thematically fitting)
        // case where grafleysa is stacked on a haugbol-bound item, since
        // THAT one survives death and stays equipped. A fully complete
        // version would need to snapshot equipment at the moment of
        // death instead of checking current state after the fact - real,
        // separate work in RecentDeaths/DeathHooks, not done here.
        if (com.dragonspeech.enchant.EquippedEnchantments.has(departed, "grafleysa")) {
            return EffectResult.failure("The thread strains and will not hold - something refuses to let them be called back.");
        }

        ServerPlayer revived = departed;
        if (!departed.isAlive()) {
            revived = server.getPlayerList().respawn(departed, false, Entity.RemovalReason.KILLED);
        }

        if (caster.level() instanceof ServerLevel level && revived.level().dimension().equals(level.dimension())) {
            revived.teleportTo(level, thread.position().x, thread.position().y, thread.position().z,
                revived.getYRot(), revived.getXRot());
        }

        revived.setHealth(Math.max(1f, revived.getMaxHealth() / 2f));
        revived.getFoodData().setFoodLevel(6);
        RecentDeaths.clear(thread.playerId());

        com.dragonspeech.scar.ScarService.applyRandomScar(revived);
        revived.sendSystemMessage(Component.literal(
            "You are pulled back across, gasping - and something is missing. The crossing has taken its toll."));
        return EffectResult.success(1, "The thread holds. Life answers the word, and returns.");
    }

    /** Recreates the mob from its saved shape and NBT - there is no "respawn this mob" API, so the working truly rebuilds it. */
    private static EffectResult reviveMob(ServerPlayer caster, MinecraftServer server, RecentDeaths.MobDeathRecord thread) {
        if (!(caster.level() instanceof ServerLevel level) || !level.dimension().equals(thread.dimension())) {
            return EffectResult.failure("The thread does not reach into this place.");
        }

        Entity restored = thread.type().create(level);
        if (restored == null) {
            return EffectResult.failure("The shape will not hold - the word finds nothing to pour life into.");
        }

        restored.load(thread.savedData());
        restored.setPos(thread.position().x, thread.position().y, thread.position().z);
        if (restored instanceof LivingEntity living) {
            living.setHealth(Math.max(1f, living.getMaxHealth() / 2f));
        }

        level.addFreshEntity(restored);
        RecentDeaths.clearMob(thread);

        return EffectResult.success(1, "The thread holds, and the creature draws breath again - lesser than it was, but living.");
    }

    private static boolean isSelfSpoken(EffectInvocation invocation) {
        return invocation.composition().scopeWord().map(Word::scopeSelf).orElse(false);
    }

    /** Whether any BINDING-category word was spoken alongside the resurrection verb - this is what distinguishes the persistent ward-form from the 5-second timing-form. */
    private static boolean hasBindingWord(EffectInvocation invocation) {
        return !invocation.composition().wordsOf(com.dragonspeech.word.WordCategory.BINDING).isEmpty();
    }

    /** Read-only peek so cost can reflect a mob thread's size before the cast is committed to. */
    private static Optional<Float> peekMobMass(EffectInvocation invocation) {
        var caster = invocation.caster();
        RecentDeaths.MobDeathRecord mobThread = RecentDeaths.findMobNear(
            caster.position(), caster.level().dimension(), REACH_BLOCKS, caster.level().getGameTime());
        RecentDeaths.DeathRecord playerThread = RecentDeaths.findNear(
            caster.position(), caster.level().dimension(), REACH_BLOCKS, caster.level().getGameTime());
        boolean useMob = mobThread != null && (playerThread == null || mobThread.gameTime() > playerThread.gameTime());
        return useMob ? Optional.of(mobThread.massFactor()) : Optional.empty();
    }

    /** Public entry so DeathHooks can apply the same scar after a ward-form (pre-paid) revival. */
    /** Public entry so DeathHooks/ReviveScheduler can apply a scar after ANY form of revival - the crossing always leaves its mark. */
    public static void applyScarPublic(ServerPlayer revived) {
        com.dragonspeech.scar.ScarService.applyRandomScar(revived);
    }
}
