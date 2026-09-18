package com.dragonspeech.shade;

import com.dragonspeech.elf.ElderElfEntity;
import com.dragonspeech.elf.ElfEntity;
import com.dragonspeech.human.HumanMageEntity;
import com.dragonspeech.mob.casting.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * "Extremely powerful and hostile... a vast amount of magic and can use
 * many spells... They will attack players, elves, humans (both hostile
 * and neutral), villagers, iron golems, etc." per the design brief.
 * MobPowerTier.CATASTROPHIC gives it the biggest energy pool and shortest
 * cooldown of any mob in this pass.
 *
 * Per explicit follow-up direction, Shades no longer all share one fixed
 * vocabulary - see applyElementalAffinity(). Also implements Warded -
 * see MobWards for the current (vocabulary-gated, real-durability) ward
 * design, and ElderElfEntity's class doc for why activeWards is built in
 * the constructor BODY (after vocabulary is populated) rather than as a
 * field initializer.
 *
 * Extends Monster (not SpellcastingMobEntity) so it gets normal vanilla
 * hostile-mob classification - despawn rules, the hostile-mob spawn cap,
 * Enemy-based helpers elsewhere in the codebase that key off Monster/Enemy.
 * Since Java has no multiple inheritance, it implements SpellcastingMob
 * (and Warded) directly and holds its own SpellcasterState field instead
 * of extending SpellcastingMobEntity - see that class's and
 * SpellcasterState's comments.
 */
public class ShadeEntity extends Monster implements SpellcastingMob, Warded, WardLearner {

    private static final List<MobWards.WardType> KNOWN_WARD_TYPES = List.of(
        MobWards.WardType.MELEE, MobWards.WardType.FIRE, MobWards.WardType.EXPLOSION, MobWards.WardType.MAGIC
    );

    /** "Advanced" (see WardLearner/applyElementalAffinity) starts at 3 elements - the ~30% roll, not the 70% baseline of exactly 2. */
    private static final int ADVANCED_ELEMENT_THRESHOLD = 3;

    /**
     * The specific catastrophic words explicitly approved for a Shade to
     * rarely know - "these words are allowed for a shade to have (a
     * shade can have 1 to 2 of these)... should be rare." Deliberately
     * NOT the full 50-word catastrophic list - the excluded ones are
     * either item-enchant-binding/passive-buff words (a different
     * content family entirely, not something a hostile mob would ever
     * "cast") or ones judged too extreme even for a rare exception
     * (aftrlifga/hambinda in particular).
     */
    private static final List<String> RARE_CATASTROPHIC_WORDS = List.of(
        "kallbinda", "steinbinda", "thyngdbinda", "eitrbinda", "graedbinda",
        "kyrra", "heimbinda", "tidbinda", "aflflyta", "lifflyta", "brynna",
        "kringla", "bur", "samvefja", "seidverja"
    );

    private final SpellcasterState spellState = new SpellcasterState(MobPowerTier.CATASTROPHIC);
    private final Map<MobWards.WardType, MobWards.WardInstance> activeWards;
    private int elementalDomainCount;

    // WardLearner state - deliberately NOT persisted, see that interface's own doc.
    private ResourceLocation lastCastVerb;
    private final Map<UUID, Set<ResourceLocation>> blockedVerbsByTarget = new HashMap<>();

    public ShadeEntity(EntityType<? extends ShadeEntity> type, Level level) {
        super(type, level);
        spellState.vocabulary().learnAll(MobWordPools.SHADE_SHARED);
        applyElementalAffinity();
        applyRareCatastrophicKnowledge();
        this.activeWards = MobWards.rollStartingWards(this.getRandom(), MobPowerTier.CATASTROPHIC, this.vocabulary());
    }

    /**
     * "I want each shade to use different magics. From fire, water,
     * earth, lightning, air, mind, All of them... There should also be
     * some Shades that can use multiple or all of the elements" per
     * direction, now combined with the explicit "at least 2 elements"
     * minimum for Shade: 70% get exactly 2, 18% get 3-4, 12% get all
     * five - NEVER just 1, unlike the first version of this method.
     * Domain order is shuffled per-Shade (seeded from its own UUID, not
     * the shared world random, so this doesn't perturb any other random
     * draw sequence) so which elements a multi-element Shade gets also
     * varies individual to individual, not just the count.
     */
    private void applyElementalAffinity() {
        float roll = this.getRandom().nextFloat();
        int elementCount = roll < 0.12f ? 5 : roll < 0.30f ? (3 + this.getRandom().nextInt(2)) : 2;
        this.elementalDomainCount = elementCount;

        List<List<ResourceLocation>> domains = new ArrayList<>(List.of(
            MobWordPools.SHADE_FIRE, MobWordPools.SHADE_WATER, MobWordPools.SHADE_EARTH,
            MobWordPools.SHADE_AIR, MobWordPools.SHADE_MIND
        ));
        Collections.shuffle(domains, new Random(this.getUUID().getLeastSignificantBits()));
        for (int i = 0; i < Math.min(elementCount, domains.size()); i++) {
            spellState.vocabulary().learnAll(domains.get(i));
        }
    }

    /**
     * "These words are allowed for a shade to have (a shade can have 1
     * to 2 of these catastrophic words)... should be rare" per explicit
     * direction. Only "advanced" Shades (3+ elements) are even eligible,
     * and even among those, most still roll none - roughly 4-5% of ALL
     * Shades end up with any, so encountering one that does should read
     * as "this particular Shade is unusually dangerous," not a common
     * occurrence. Seeded independently of the elemental-affinity roll
     * (a different XOR mask on the same UUID) so the two don't correlate
     * in an obvious pattern.
     */
    private void applyRareCatastrophicKnowledge() {
        if (elementalDomainCount < ADVANCED_ELEMENT_THRESHOLD) {
            return;
        }
        if (this.getRandom().nextFloat() >= 0.15f) {
            return; // most advanced Shades still know none
        }
        int count = this.getRandom().nextFloat() < 0.85f ? 1 : 2;

        List<String> pool = new ArrayList<>(RARE_CATASTROPHIC_WORDS);
        Collections.shuffle(pool, new Random(this.getUUID().getMostSignificantBits() ^ 0x5A11_C0FFEEL));
        for (int i = 0; i < Math.min(count, pool.size()); i++) {
            spellState.vocabulary().learnAll(List.of(com.dragonspeech.DragonSpeech.id(pool.get(i))));
        }
    }

    /** "This should be another aspect of the ADVANCED shades" per direction - only the rarer multi-element rolls (3+) actually learn from being warded off; a baseline 2-element Shade doesn't. */
    public boolean isAdvanced() {
        return elementalDomainCount >= ADVANCED_ELEMENT_THRESHOLD;
    }

    // ---------------------------------------------------------------
    // WardLearner
    // ---------------------------------------------------------------

    @Override
    public void setLastCastVerb(ResourceLocation verbId) {
        this.lastCastVerb = verbId;
    }

    @Override
    public ResourceLocation getLastCastVerb() {
        return lastCastVerb;
    }

    /**
     * "Instead of [some shades more dangerous than others], let's make
     * ALL shades that dangerous instead of just a percentage" per
     * explicit direction - ward-learning (see WardLearner's own doc) is
     * no longer gated to isAdvanced() at all; every Shade now adapts
     * against a target's wards over the course of a fight, not just the
     * rarer multi-element rolls. isAdvanced()/ADVANCED_ELEMENT_THRESHOLD
     * are kept for the RARE catastrophic word system below, which you
     * asked to stay rare and gated - this is specifically about combat
     * capability, not vocabulary.
     */
    @Override
    public void rememberBlocked(UUID targetId, ResourceLocation verbId) {
        blockedVerbsByTarget.computeIfAbsent(targetId, id -> new HashSet<>()).add(verbId);
    }

    @Override
    public boolean isKnownBlocked(UUID targetId, ResourceLocation verbId) {
        Set<ResourceLocation> blocked = blockedVerbsByTarget.get(targetId);
        return blocked != null && blocked.contains(verbId);
    }

    /**
     * "Shades are meant to be extremely difficult to beat... for the
     * most part, shades will win [against a single Elder Elf], but not
     * all the time" per explicit direction - bumped from 40 HP / 6
     * damage to make that gap (vs. Elder Elf's 30 HP / 3 damage)
     * decisive rather than marginal. Combined with the ward-learning
     * change above and the more aggressive cast-strategy bias in
     * MobCastStrategy.choose, this is meant to make an Elder Elf the
     * clear underdog in a 1-on-1, not a coin flip.
     */
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
            .add(Attributes.MAX_HEALTH, 50.0)
            .add(Attributes.MOVEMENT_SPEED, 0.3)
            .add(Attributes.FOLLOW_RANGE, 48.0)
            .add(Attributes.ATTACK_DAMAGE, 8.0)
            .add(Attributes.ARMOR, 4.0);
    }

    @Override
    public SpellcasterState spellState() {
        return spellState;
    }

    @Override
    public Map<MobWards.WardType, MobWards.WardInstance> activeWards() {
        return activeWards;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        MobWards.WardResult result = MobWards.applyWards(activeWards, this, source, amount);
        if (result.blocked()) {
            return false; // no hurt sound/red flash - see MobWards.applyWards' own doc
        }
        recordDamagePressure(result.remainingDamage());
        return super.hurt(source, result.remainingDamage());
    }

    @Override
    public LivingEntity asEntity() {
        return this;
    }

    /**
     * "They can despawn fine. However, if I learned their true name/
     * begin to learn their true name, then they can continue and have
     * permanent persistence" per explicit direction - replaces the
     * earlier blanket "always persistent like a villager" version.
     * trueNamePursued flips permanently true (and is saved) the first
     * time ANY online player in this Shade's level has a
     * TrueNameProgress entry for it - "begin to learn" means an
     * unlocked-but-not-yet-solved entry counts too, not just full
     * knowledge. Checked periodically (every 100 ticks) rather than
     * every tick, and only until the first hit - once true, it's
     * permanent and the check stops running.
     */
    private boolean trueNamePursued = false;
    private int trueNameCheckCooldown = 0;

    @Override
    public boolean isPersistenceRequired() {
        return trueNamePursued;
    }

    private void checkTrueNamePursuit() {
        if (trueNamePursued || !(this.level() instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return;
        }
        if (trueNameCheckCooldown > 0) {
            trueNameCheckCooldown--;
            return;
        }
        trueNameCheckCooldown = 100;
        for (net.minecraft.server.level.ServerPlayer player : serverLevel.players()) {
            if (com.dragonspeech.mind.MindDataAccess.get(player).progressFor(this.getUUID()).isPresent()) {
                trueNamePursued = true;
                return;
            }
        }
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        spellState.tick();
        checkTrueNamePursuit();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.put("dragonspeech_spellcaster", spellState.save());
        tag.putBoolean("dragonspeech_true_name_pursued", trueNamePursued);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.trueNamePursued = tag.getBoolean("dragonspeech_true_name_pursued");
        if (tag.contains("dragonspeech_spellcaster")) {
            spellState.load(tag.getCompound("dragonspeech_spellcaster"));
        }
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new MobSpellCastGoal(this, List.of(
            SpellIntent.OFFENSE, SpellIntent.CROWD_CONTROL, SpellIntent.SUMMON_HELP, SpellIntent.MOBILITY
        ), 13.0));
        this.goalSelector.addGoal(2, new MobKeepDistanceGoal(this, 5.0, 10.0, 1.15));
        this.goalSelector.addGoal(3, new RandomStrollGoal(this, 0.7));
        this.goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 10.0f));
        this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));
        this.goalSelector.addGoal(6, new MobSelfWardGoal(this, this, MobPowerTier.CATASTROPHIC, KNOWN_WARD_TYPES));
        // MobDetectionCastGoal deliberately NOT registered here - per
        // explicit direction, stamina/mark detection is a player-facing
        // spell, not something mobs should cast on their own.

        // Priority order matches the design brief's own listing - players
        // first, then the other spellcasting races (hostile Human Mages
        // included: Shades don't discriminate), then villagers/golems.
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, ElfEntity.class, true));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, ElderElfEntity.class, true));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, HumanMageEntity.class, true));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Villager.class, true));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, IronGolem.class, true));
    }
}
