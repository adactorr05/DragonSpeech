package com.dragonspeech.mob.casting;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Starting wards for Shade and Elder Elf. Rewritten per explicit
 * follow-up direction after two real problems with the first version:
 *
 * 1. "It should relate to: the words they know... usually around 1 to 2
 *    wards" - the original rollStartingWards ignored vocabulary entirely
 *    and independently rolled each of 5 ward types at 40% each, which in
 *    practice produced "all/most of them" rather than 1-2. Now: the
 *    eligible ward types are derived from which ward WORDS the mob
 *    actually knows (see wardFor/eligibleWards), melee is guaranteed
 *    IF known, and at most one or two more are added on top with
 *    decreasing probability (~55% for a 2nd, ~15% for a 3rd) - "usually
 *    1 to 2."
 *
 * 2. "Wards either have a huge amount of durability, or something else"
 *    - true: the original had no durability concept AT ALL, a ward was a
 *    flat percentage reduction applied forever until MELEE/FIRE/etc
 *    matched, with no way to run out. Wards are now a real depleting
 *    resource (WardInstance) - each absorbed hit drains it by the amount
 *    it blocked, and once empty the ward drops out of the active set
 *    (until MobSelfWardGoal re-raises it). NOT tied to the mob's own
 *    mysticalEnergy/stamina - kept as its own separate pool, scaled by
 *    MobPowerTier, so hitting a mob's wards doesn't drain its ability to
 *    cast spells (a deliberate choice; say the word if you'd rather they
 *    share one pool instead).
 *
 * A ward absorbing a hit spawns COLORED dust particles matching this
 * ward type's color EXACTLY as WardRingRenderer already colors the same
 * ward types for players.
 */
public final class MobWards {

    private MobWards() {}

    public enum WardType { MELEE, PROJECTILE, FIRE, EXPLOSION, MAGIC, FALL }

    /** One active ward on one entity - a real, depleting resource, not a permanent flag. */
    public static final class WardInstance {
        private final WardType type;
        private final float maxDurability;
        private float durability;

        public WardInstance(WardType type, float maxDurability) {
            this.type = type;
            this.maxDurability = maxDurability;
            this.durability = maxDurability;
        }

        public WardType type() { return type; }
        public float durability() { return durability; }
        public float maxDurability() { return maxDurability; }

        private void drain(float amount) {
            durability = Math.max(0f, durability - amount);
        }

        private boolean isDepleted() {
            return durability <= 0f;
        }
    }

    /** Scales with MobPowerTier - "elders and shades will know a lot more about wards" extends to how tough those wards are too, not just how many types are available. */
    public static float durabilityFor(MobPowerTier tier) {
        return switch (tier) {
            case APPRENTICE -> 20f;
            case ADEPT -> 30f;
            case ELDER -> 50f;
            case CATASTROPHIC -> 80f;
        };
    }

    /** Which WardType a known binding word represents - null if it isn't a ward word. */
    public static WardType wardFor(String trueName) {
        return switch (trueName) {
            case "hoggverja" -> WardType.MELEE;
            case "verja" -> WardType.PROJECTILE;
            case "eldverja" -> WardType.FIRE;
            case "sprengverja" -> WardType.EXPLOSION;
            case "seidverja" -> WardType.MAGIC;
            case "fallverja" -> WardType.FALL;
            default -> null;
        };
    }

    /** Every WardType this mob could ever roll or re-raise, derived from which ward words it actually knows - "should relate to the words they know" per direction. */
    public static List<WardType> eligibleWards(MobVocabulary vocabulary) {
        List<WardType> eligible = new ArrayList<>();
        for (ResourceLocation id : vocabulary.words()) {
            WardType type = wardFor(id.getPath());
            if (type != null) {
                eligible.add(type);
            }
        }
        return eligible;
    }

    /**
     * Rolls the starting ward set from ONLY the ward types this
     * particular mob's vocabulary makes eligible - melee is guaranteed
     * if (and only if) "hoggverja" is among them, matching "should
     * always have a ward against melee attacks" without breaking the
     * new "must relate to known words" rule for a mob that somehow
     * doesn't know it. On top of melee: one more ward at ~55%, a second
     * at ~15% - "usually around 1 to 2."
     */
    public static Map<WardType, WardInstance> rollStartingWards(RandomSource random, MobPowerTier tier, MobVocabulary vocabulary) {
        Map<WardType, WardInstance> wards = new EnumMap<>(WardType.class);
        List<WardType> eligible = eligibleWards(vocabulary);
        if (eligible.isEmpty()) {
            return wards;
        }
        float durability = durabilityFor(tier);

        if (eligible.contains(WardType.MELEE)) {
            wards.put(WardType.MELEE, new WardInstance(WardType.MELEE, durability));
        }

        List<WardType> remaining = new ArrayList<>(eligible);
        remaining.remove(WardType.MELEE);
        Collections.shuffle(remaining, new Random(random.nextLong()));

        if (!remaining.isEmpty() && random.nextFloat() < 0.55f) {
            WardType extra = remaining.get(0);
            wards.put(extra, new WardInstance(extra, durability));
        }
        if (remaining.size() > 1 && random.nextFloat() < 0.15f) {
            WardType extra = remaining.get(1);
            wards.put(extra, new WardInstance(extra, durability));
        }
        return wards;
    }

    /** Copied verbatim from WardRingRenderer's own per-type colors, so a ward absorbing a hit reads as the same color language players already see on their own wards. */
    public static Vector3f colorOf(WardType type) {
        return switch (type) {
            case PROJECTILE -> new Vector3f(0.90f, 0.78f, 0.30f); // gold
            case FIRE -> new Vector3f(0.95f, 0.35f, 0.15f);       // ember red
            case FALL -> new Vector3f(0.40f, 0.85f, 0.40f);       // green
            case EXPLOSION -> new Vector3f(0.95f, 0.55f, 0.15f);  // orange
            case MELEE -> new Vector3f(0.80f, 0.85f, 0.90f);      // pale steel
            case MAGIC -> new Vector3f(0.65f, 0.35f, 0.90f);      // violet
        };
    }

    /**
     * Spawns a ward-colored particle burst + sound when a ward actually
     * absorbs a hit, drains its durability by the amount absorbed, and
     * removes it from the active map entirely once depleted (with a
     * slightly bigger "shatter" particle burst so a ward running out is
     * as visible as one triggering).
     */
    /**
     * Result of a ward check: either the hit was fully blocked (nothing
     * should reach vanilla's hurt() at all - no hurt sound, no red
     * flash, no damage; see WardResult.blocked()) or it wasn't (the
     * caller should proceed with `remainingDamage` as normal, unchanged
     * if no ward matched).
     */
    public record WardResult(float remainingDamage, boolean blocked) {}

    /**
     * "I would prefer an approach that stops the damage from hitting
     * you vs how it hits but nullifies" per explicit direction - wards
     * now fully block a hit (not partially reduce it) for as long as
     * they have durability, and the caller (ElderElfEntity/ShadeEntity's
     * hurt() overrides) skips calling super.hurt() ENTIRELY when
     * blocked, so no hurt sound or red flash happens - only this
     * method's own block sound/particles, which is deliberately kept
     * ("I do like that it does make a blocking sound... that is a good
     * touch that I want kept"). Durability is consumed by the FULL raw
     * incoming amount now (not 65% of it, like the old partial-reduction
     * version) - a ward is a real "how many good hits can I eat before
     * this breaks" budget, not a permanent damage-percentage discount.
     *
     * PROJECTILE additionally discards the actual projectile entity
     * (source.getDirectEntity()) and plays a distinct deflection burst -
     * "genuinely stopping it before impact... bounce off an invisible
     * shield" per direction. This happens the same instant the hit would
     * have landed rather than truly pre-impact (a full pre-impact
     * interception system would need to scan for and redirect incoming
     * projectiles every tick, a much bigger change) - but since the
     * projectile is destroyed and no damage/sound/flash ever reaches the
     * defender, the visible result is the same: it looks deflected, not
     * absorbed.
     */
    public static WardResult applyWards(Map<WardType, WardInstance> wards, LivingEntity self, DamageSource source, float amount) {
        if (wards.isEmpty()) {
            return new WardResult(amount, false);
        }
        boolean fire = source.is(DamageTypeTags.IS_FIRE);
        boolean explosion = source.is(DamageTypeTags.IS_EXPLOSION);
        boolean fall = source.is(DamageTypeTags.IS_FALL);
        boolean magic = source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC);
        boolean projectile = source.getDirectEntity() instanceof Projectile;

        WardType matchedType = null;
        if (wards.containsKey(WardType.FIRE) && fire) matchedType = WardType.FIRE;
        else if (wards.containsKey(WardType.EXPLOSION) && explosion) matchedType = WardType.EXPLOSION;
        else if (wards.containsKey(WardType.FALL) && fall) matchedType = WardType.FALL;
        else if (wards.containsKey(WardType.MAGIC) && magic) matchedType = WardType.MAGIC;
        else if (wards.containsKey(WardType.PROJECTILE) && projectile) matchedType = WardType.PROJECTILE;
        else if (wards.containsKey(WardType.MELEE) && source.getEntity() instanceof LivingEntity
                && !fire && !explosion && !magic && !projectile) matchedType = WardType.MELEE;

        if (matchedType == null) {
            return new WardResult(amount, false);
        }
        WardInstance instance = wards.get(matchedType);
        instance.drain(amount); // full raw amount - see class doc, no longer a 65% partial reduction

        // See WardLearner: an "advanced" Shade remembers that whatever
        // verb it just cast (see MobCastExecutor.tryCast) got blocked
        // against THIS specific target, so MobSpellComposer can favor
        // something else next time.
        if (source.getEntity() instanceof WardLearner learner && learner.getLastCastVerb() != null) {
            learner.rememberBlocked(self.getUUID(), learner.getLastCastVerb());
        }

        if (self.level() instanceof ServerLevel level) {
            if (matchedType == WardType.PROJECTILE && source.getDirectEntity() instanceof net.minecraft.world.entity.Entity projectileEntity) {
                projectileEntity.discard();
                level.sendParticles(ParticleTypes.CRIT, self.getX(), self.getEyeY(), self.getZ(), 12, 0.35, 0.35, 0.35, 0.08);
            }
            DustParticleOptions options = new DustParticleOptions(colorOf(matchedType), 1.2f);
            level.sendParticles(options, self.getX(), self.getEyeY(), self.getZ(), 10, 0.3, 0.3, 0.3, 0.02);
            level.playSound(null, self.blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.NEUTRAL, 0.6f, 1.4f);

            if (instance.isDepleted()) {
                level.sendParticles(ParticleTypes.POOF, self.getX(), self.getEyeY(), self.getZ(), 15, 0.4, 0.4, 0.4, 0.05);
                level.playSound(null, self.blockPosition(), SoundEvents.SHIELD_BREAK, SoundSource.NEUTRAL, 0.7f, 1.0f);
            }
        }
        if (instance.isDepleted()) {
            wards.remove(matchedType);
        }
        return new WardResult(0f, true);
    }
}
