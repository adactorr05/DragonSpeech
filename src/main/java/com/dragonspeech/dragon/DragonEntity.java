package com.dragonspeech.dragon;

import com.dragonspeech.DragonSpeech;
// FIX: "package com.dragonspeech.client.dragon does not exist" -
// Fabric's split source sets are strict (unlike NeoForge, which
// doesn't enforce this the same way) - src/main (this file) genuinely
// cannot import anything from src/client at compile time, full stop.
// DragonAnimator needs its own client-only existence for real-time
// rendering state, but this entity (server-side) still needs
// somewhere to hold a reference to it. Storing as a generic Object
// field instead of a typed DragonAnimator reference - the type itself
// only matters to client-only callers (DragonModel/DragonAnimator,
// both in src/client), which cast it back after retrieving it.
import com.dragonspeech.dragon.breed.DragonBreed;
import com.dragonspeech.dragon.breed.DragonBreedRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.control.BodyRotationControl;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.FlyingAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SaddleItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;

import org.jetbrains.annotations.Nullable;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static net.minecraft.world.entity.ai.attributes.Attributes.*;

/**
 * The dragon entity itself - flight, growth, riding, sounds, breed
 * traits. Structure and the vast majority of logic ported near-verbatim
 * from Dragon Mounts Legacy's TameableDragon
 * (com.github.kay9.dragonmounts.dragon.TameableDragon, GPL-3.0,
 * https://github.com/TheRealKingslayer1/Dragon-Mounts-Legacy, original
 * author credit: Nico Bergemann and Kay9). Class kept named DragonEntity
 * (not renamed to match theirs) so DragonSpeechEntities/
 * DragonEggBlockItem/etc don't all need touching too.
 *
 * REAL, DELIBERATE CHANGES from the original (not just renames):
 *
 * 1. BREED TYPE: Dragon Mounts Legacy stores Holder<DragonBreed> from
 *    Mojang's dynamic-registry system. This project's DragonBreed
 *    (see com.dragonspeech.dragon.breed) is a simpler,
 *    ResourceLocation-keyed record loaded via DragonBreedRegistry
 *    (same pattern as WordRegistry) - see DragonBreed's own doc for
 *    why. So the synced/saved data here is a breed ID
 *    (Optional<String>, resolved through DragonBreedRegistry.get()
 *    lazily), not a Holder.
 *
 * 2. TAMING -> BONDING: per explicit direction, "the taming system
 *    here [gets] replaced with my bonding system. They can only be
 *    bonded when you hatch the egg. If there are dragons that spawn,
 *    they are wild dragons and cannot be tamed." isTame()/isOwnedBy()
 *    are overridden to delegate to bondedOwner()/isBondedTo() (this
 *    project's own existing bonding fields, same names/shape as the
 *    OLD DragonEntity already used) instead of vanilla TamableAnimal's
 *    own tame-by-taming-item system. mobInteract's item-based taming
 *    branch is removed entirely - a dragon either already has a
 *    bondedOwner (set at hatch time, wired separately, not in this
 *    file) or it never can.
 *
 * 3. AI GOALS: registerGoals() references DragonFollowOwnerGoal,
 *    DragonBreedGoal, DragonMoveController, DragonBodyController -
 *    these are separate files, also adapted from Dragon Mounts Legacy,
 *    not yet ported as of this delivery (next piece). Referenced here
 *    so this file's shape is final, but won't compile standalone until
 *    those exist.
 */
@SuppressWarnings({"deprecation", "SameReturnValue"})
public class DragonEntity extends TamableAnimal implements Saddleable, FlyingAnimal, PlayerRideable {

    public static final double BASE_SPEED_GROUND = 0.3;
    public static final double BASE_SPEED_FLYING = 0.32;
    public static final double BASE_DAMAGE = 8;
    public static final double BASE_HEALTH = 60;
    public static final double BASE_FOLLOW_RANGE = 16;
    public static final int BASE_KB_RESISTANCE = 1;
    public static final float BASE_WIDTH = 2.75f;
    public static final float BASE_HEIGHT = 2.75f;
    public static final int BASE_REPRO_LIMIT = 2;

    /** FIX: EntityDataSerializers.OPTIONAL_STRING doesn't exist in this Minecraft version's real API - plain STRING with "" as the "no breed set yet" sentinel instead of Optional. */
    private static final EntityDataAccessor<String> DATA_BREED_ID = SynchedEntityData.defineId(DragonEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> DATA_SADDLED = SynchedEntityData.defineId(DragonEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_AGE = SynchedEntityData.defineId(DragonEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Optional<UUID>> DATA_BONDED_OWNER = SynchedEntityData.defineId(DragonEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Float> DATA_DRAGON_STAMINA = SynchedEntityData.defineId(DragonEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_HEART_GIVEN = SynchedEntityData.defineId(DragonEntity.class, EntityDataSerializers.BOOLEAN);
    /** Ticks since first reaching adult - vanilla's own aging concept stops meaning anything past age=0, so this is a genuinely separate counter, not a reuse/extension of the existing age field. See isElder()/isAncient()/getAgeScale()'s own docs for how this drives elder/ancient. */
    private static final EntityDataAccessor<Integer> DATA_POST_ADULT_TICKS = SynchedEntityData.defineId(DragonEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_OPTION_FOLLOWING = SynchedEntityData.defineId(DragonEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_OPTION_AGGRESSIVE_ASSIST = SynchedEntityData.defineId(DragonEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_OPTION_STAY = SynchedEntityData.defineId(DragonEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_ATTACK_NEARBY_HOSTILE = SynchedEntityData.defineId(DragonEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_ATTACK_NEARBY_NEUTRAL = SynchedEntityData.defineId(DragonEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_ATTACK_NEARBY_PASSIVE = SynchedEntityData.defineId(DragonEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_USE_DRAGON_STAMINA = SynchedEntityData.defineId(DragonEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_STAMINA_BEFORE_OWN = SynchedEntityData.defineId(DragonEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_STAMINA_LIMITER_PERCENT = SynchedEntityData.defineId(DragonEntity.class, EntityDataSerializers.INT);

    public static final String NBT_BREED = "Breed";
    private static final String NBT_SADDLED = "Saddle";
    private static final String NBT_REPRO_COUNT = "ReproCount";
    private static final String NBT_BONDED_OWNER = "BondedOwner";

    public static final int AGE_UPDATE_INTERVAL = 100;
    public static final ResourceLocation AGE_ATTR_MODIFIER_ID = DragonSpeech.id("age_scale_modifier");
    public static final int GROUND_CLEARENCE_THRESHOLD = 3;
    private final EntityDimensions SITTING_DIMENSIONS = EntityDimensions.scalable(BASE_WIDTH, 2.15f).withEyeHeight(2.58f);

    /**
     * The only way this server-side class gets a client-only
     * DragonAnimator instance without ever directly referencing that
     * class - set once, client-side only, by DragonSpeechClient's own
     * init (DragonEntity.animatorFactory = DragonAnimator::new). Stays
     * null on a dedicated server, which is exactly correct - nothing
     * needs a real animator there.
     */
    public static java.util.function.Function<DragonEntity, Object> animatorFactory = null;

    /**
     * "Make these dragon controls changeable in keybinds as well"
     * per explicit direction - now backed by a real, Controls-menu-
     * remappable KeyMapping (registered in DragonSpeechClient), not a
     * hardcoded key check. Same split-source-set-safe pattern as
     * animatorFactory: set once, client-side only. Defaults to "always
     * false", exactly correct on a dedicated server.
     */
    public static java.util.function.BooleanSupplier descendKeyProvider = () -> false;

    /**
     * "ctrl functions as a sprint... same thing please" per explicit
     * direction - reuses vanilla's own real Sprint keybind (its
     * default IS left ctrl already), reading the raw key state rather
     * than the derived isSprinting() entity flag, which didn't
     * actually engage while riding. Same safe-default pattern as
     * descendKeyProvider.
     */
    public static java.util.function.BooleanSupplier sprintKeyProvider = () -> false;

    private final Object animator;
    private int reproductionCount;
    private float ageProgress = 1;
    private boolean flying;
    private boolean nearGround;
    /** Last collision-free position; used as a safety net for the dragon's unusually large body. */
    private Vec3 lastCollisionSafePos;

    // Double-tap-W sprint tracking removed - replaced by the real,
    // remappable sprint keybind (see sprintKeyProvider's own doc).
    // Checked Saints Dragons' actual source: they use a dedicated key
    // for this too, not a double-tap gesture, so this now matches
    // their real design rather than keeping an invented hybrid.

    private final GroundPathNavigation groundNavigation;
    private final FlyingPathNavigation flyingNavigation;

    public DragonEntity(EntityType<? extends DragonEntity> type, Level level) {
        super(type, level);

        noCulling = true;

        moveControl = new com.dragonspeech.dragon.ai.DragonMoveController(this);
        animator = (level.isClientSide && animatorFactory != null) ? animatorFactory.apply(this) : null;

        flyingNavigation = new FlyingPathNavigation(this, level);
        groundNavigation = new GroundPathNavigation(this, level);

        flyingNavigation.setCanFloat(true);
        groundNavigation.setCanFloat(true);

        navigation = groundNavigation;
    }

    @Override
    public BodyRotationControl createBodyControl() {
        return new com.dragonspeech.dragon.ai.DragonBodyController(this);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(MOVEMENT_SPEED, BASE_SPEED_GROUND)
                .add(MAX_HEALTH, BASE_HEALTH)
                .add(FOLLOW_RANGE, BASE_FOLLOW_RANGE)
                .add(KNOCKBACK_RESISTANCE, BASE_KB_RESISTANCE)
                .add(ATTACK_DAMAGE, BASE_DAMAGE)
                .add(ARMOR, 0.0)
                .add(FLYING_SPEED, BASE_SPEED_FLYING);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new FloatGoal(this));
        goalSelector.addGoal(2, new SitWhenOrderedToGoal(this));
        // Range/cooldown are my own reasonable defaults, not specified
        // anywhere - easy to retune if 16 blocks/5 seconds isn't the
        // feel you want.
        goalSelector.addGoal(3, new DragonBreathAttackGoal(this, 16.0, 100));
        goalSelector.addGoal(3, new MeleeAttackGoal(this, 1, true));
        goalSelector.addGoal(5, new com.dragonspeech.dragon.ai.DragonFollowOwnerGoal(this, 1f, 10f, 3.5f, 32f));
        goalSelector.addGoal(5, new com.dragonspeech.dragon.ai.DragonBreedGoal(this));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.85f));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, LivingEntity.class, 16f));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));

        targetSelector.addGoal(2, new HurtByTargetGoal(this));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);

        builder.define(DATA_BREED_ID, "");
        builder.define(DATA_SADDLED, false);
        builder.define(DATA_AGE, 0);
        builder.define(DATA_BONDED_OWNER, Optional.empty());
        builder.define(DATA_DRAGON_STAMINA, 0f);
        builder.define(DATA_HEART_GIVEN, false);
        builder.define(DATA_POST_ADULT_TICKS, 0);
        builder.define(DATA_OPTION_FOLLOWING, true);
        builder.define(DATA_OPTION_AGGRESSIVE_ASSIST, false);
        builder.define(DATA_OPTION_STAY, false);
        builder.define(DATA_ATTACK_NEARBY_HOSTILE, false);
        builder.define(DATA_ATTACK_NEARBY_NEUTRAL, false);
        builder.define(DATA_ATTACK_NEARBY_PASSIVE, false);
        builder.define(DATA_USE_DRAGON_STAMINA, false);
        builder.define(DATA_STAMINA_BEFORE_OWN, true);
        builder.define(DATA_STAMINA_LIMITER_PERCENT, 20);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> data) {
        if (DATA_BREED_ID.equals(data)) {
            updateAgeProperties();
        } else if (DATA_FLAGS_ID.equals(data)) {
            refreshDimensions();
        } else if (DATA_AGE.equals(data)) {
            updateAgeProperties();
        } else {
            super.onSyncedDataUpdated(data);
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);

        if (getBreedId() != null) {
            compound.putString(NBT_BREED, getBreedId().toString());
        }

        compound.putBoolean(NBT_SADDLED, isSaddled());
        compound.putInt(NBT_REPRO_COUNT, reproductionCount);
        bondedOwner().ifPresent(uuid -> compound.putUUID(NBT_BONDED_OWNER, uuid));
        compound.putFloat("DragonStamina", dragonStamina());
        compound.putBoolean("HeartGiven", heartGiven());
        compound.putInt("PostAdultTicks", entityData.get(DATA_POST_ADULT_TICKS));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        ResourceLocation breedId = compound.contains(NBT_BREED) ? ResourceLocation.tryParse(compound.getString(NBT_BREED)) : null;
        if (breedId == null || DragonBreedRegistry.get(breedId) == null) {
            // Wasn't assigned one (summon command?) or its owning
            // addon mod isn't installed this session - fall back
            // rather than leave the entity with no breed at all.
            DragonBreed fallback = DragonBreedRegistry.getOrFallback(breedId != null ? breedId : DragonSpeech.id("fire"));
            breedId = DragonBreedRegistry.getAllBreeds().entrySet().stream()
                    .filter(e -> e.getValue() == fallback).map(java.util.Map.Entry::getKey).findFirst().orElse(null);
        }
        setBreedId(breedId);

        super.readAdditionalSaveData(compound);

        setSaddled(compound.getBoolean(NBT_SADDLED));
        this.reproductionCount = compound.getInt(NBT_REPRO_COUNT);
        if (compound.hasUUID(NBT_BONDED_OWNER)) {
            setBondedOwner(compound.getUUID(NBT_BONDED_OWNER));
        }
        entityData.set(DATA_DRAGON_STAMINA, compound.getFloat("DragonStamina"));
        entityData.set(DATA_HEART_GIVEN, compound.getBoolean("HeartGiven"));
        entityData.set(DATA_POST_ADULT_TICKS, compound.getInt("PostAdultTicks"));

        getEntityData().set(DATA_AGE, getAge());
    }

    public void setBreedId(ResourceLocation breedId) {
        getEntityData().set(DATA_BREED_ID, breedId == null ? "" : breedId.toString());
    }

    @Nullable
    public ResourceLocation getBreedId() {
        String raw = getEntityData().get(DATA_BREED_ID);
        return raw.isEmpty() ? null : ResourceLocation.tryParse(raw);
    }

    /** Nullable: the breed may not be resolvable pre-deserialization, or (for an addon-mod breed) its owning mod may not be installed this session. */
    @Nullable
    public DragonBreed getBreed() {
        ResourceLocation id = getBreedId();
        return id == null ? null : DragonBreedRegistry.get(id);
    }

    /**
     * "They can only be bonded when you hatch the egg... wild dragons
     * cannot be tamed" per explicit direction - this replaces
     * TamableAnimal's own owner-uuid-based tame system entirely.
     * bondedOwner()/setBondedOwner()/isBondedTo() are this project's
     * OWN pre-existing bonding fields (same names/shape the old
     * DragonEntity already used, kept for consistency with the rest of
     * the mod's bonding code, e.g. PlayerBondData) - just re-homed onto
     * this new entity. Setting the owner is NOT done anywhere in this
     * file (no item-based taming exists here at all) - it's set once,
     * at hatch time, by whatever code handles hatching your egg (not
     * yet built - phase 2, alongside the habitat/hatching system).
     */
    public Optional<UUID> bondedOwner() {
        return getEntityData().get(DATA_BONDED_OWNER);
    }

    public void setBondedOwner(UUID owner) {
        getEntityData().set(DATA_BONDED_OWNER, Optional.of(owner));
        setOwnerUUID(owner); // keep vanilla TamableAnimal's own owner field in sync too, since isOwnedBy/getOwner are still used by some inherited AI goals
    }

    /**
     * "we will still keep the Dragon Hearts" per much earlier direction
     * - Dragon Hearts themselves are color-keyed items (DragonColor,
     * not breed), so DragonHeartService/DragonBondScreen/etc all
     * expect dragon.color() to exist. Bridges from this entity's own
     * breed to the DragonColor that heart-system code already expects,
     * using the same mapping confirmed directly against DragonColor's
     * own existing Element assignments (BLUE already carries
     * Element.LIGHTNING, WHITE already carries Element.ICE, etc - not
     * a guess, confirmed against real enum data before building the
     * breeds themselves).
     */
    public DragonColor color() {
        ResourceLocation id = getBreedId();
        String path = id == null ? "fire" : id.getPath();
        return switch (path) {
            case "fire" -> DragonColor.RED;
            case "gold" -> DragonColor.BRONZE;
            case "lightning" -> DragonColor.BLUE;
            case "ice" -> DragonColor.WHITE;
            case "forest" -> DragonColor.GREEN;
            case "void" -> DragonColor.BLACK;
            case "end" -> DragonColor.ENDER;
            default -> DragonColor.RED; // an addon-mod breed with no color mapping - RED is a reasonable, harmless fallback rather than throwing
        };
    }

    public boolean isBondedTo(Player player) {
        return bondedOwner().isPresent() && bondedOwner().get().equals(player.getUUID());
    }

    /** Used by DragonHeartService/DragonCommands/etc to find "the player's own nearby dragon" without each caller re-implementing the same search. */
    public static java.util.Optional<DragonEntity> findNearestBonded(net.minecraft.server.level.ServerPlayer player, double range) {
        net.minecraft.world.phys.AABB box = player.getBoundingBox().inflate(range);
        return player.level().getEntitiesOfClass(DragonEntity.class, box, d -> d.isBondedTo(player))
                .stream()
                .min((a, b) -> Double.compare(a.distanceToSqr(player), b.distanceToSqr(player)));
    }

    // ---------------------------------------------------------------- Dragon Stamina / Heart
    // Rebuilt (not ported from Dragon Mounts Legacy, which has no
    // equivalent mechanic at all) - "you can 'call' your dragon to
    // boost or 'feed' you its own stamina to help you with your
    // spells" and the Dragon Heart's own "give heart" one-time action
    // are core to this mod's own spellcasting-resource theme, worth
    // rebuilding rather than dropping even though nothing here traces
    // back to Dragon Mounts Legacy's code. Semantics kept as close as
    // possible to the old entity's own version - same formula shape,
    // same field names where the old ones still make sense - just
    // getAgeScale() (this entity's own continuous 0.33-1.0 growth
    // value) standing in for the old ageStage().healthMultiplier()
    // (a discrete 5-tier enum lookup that no longer exists at all).

    private static final float STAMINA_REGEN_PER_TICK = 2.0f;

    /** Same base/spread shape as the old entity's own version (150 base + up to 850 more) - just driven by getAgeScale() instead of a discrete age-stage enum. */
    public float maxDragonStamina() {
        return 150f + 850f * getAgeScale();
    }

    public float dragonStamina() {
        return entityData.get(DATA_DRAGON_STAMINA);
    }

    /** For admin/testing use (a debug/stamina command), not something normal gameplay calls directly - normal draw/regen goes through feedStaminaTo()/regenDragonStamina(). */
    public void setDragonStamina(float value) {
        entityData.set(DATA_DRAGON_STAMINA, Mth.clamp(value, 0f, maxDragonStamina()));
    }

    private void regenDragonStamina() {
        float max = maxDragonStamina();
        float current = dragonStamina();
        if (current < max) {
            entityData.set(DATA_DRAGON_STAMINA, Math.min(max, current + STAMINA_REGEN_PER_TICK));
        }
    }

    /** Only the bonded owner, only up to what the dragon itself currently has - feeding the rider genuinely costs the dragon something. Returns how much was actually transferred. */
    public float feedStaminaTo(net.minecraft.server.level.ServerPlayer rider, float requested) {
        if (!isBondedTo(rider)) {
            return 0f;
        }
        float given = Math.min(requested, dragonStamina());
        entityData.set(DATA_DRAGON_STAMINA, dragonStamina() - given);
        return given;
    }

    /** "Each dragon can only have 1 Dragon Heart. If they drop it, they cannot re-drop it if the dragon is brought back" - same one-way flag semantics as the old entity's own version, synced so DragonBondScreen can see it live client-side to decide the "Give Heart" button's visibility. */
    public boolean heartGiven() {
        return entityData.get(DATA_HEART_GIVEN);
    }

    public void setHeartGiven(boolean value) {
        entityData.set(DATA_HEART_GIVEN, value);
    }

    // ---------------------------------------------------------------- Behavior options / stamina-sharing config
    // Rebuilt with identical semantics/defaults to the old entity - per
    // "everything else should be tied back into these new models and
    // dragons," these are player-facing toggles (DragonBondScreen) with
    // no connection to Dragon Mounts Legacy's own design at all, worth
    // rebuilding rather than dropping just because they're not part of
    // what was actually ported from there.

    /** "Option A" - when on, a wandering (non-combat, non-sleeping) dragon stays near its owner instead of roaming freely. See DragonFollowOwnerGoal. */
    public boolean optionFollowing() {
        return entityData.get(DATA_OPTION_FOLLOWING);
    }

    public void setOptionFollowing(boolean value) {
        entityData.set(DATA_OPTION_FOLLOWING, value);
    }

    /** "Option B" - when on, the dragon automatically joins in on whatever its owner is fighting rather than staying passive until directly provoked itself. */
    public boolean optionAggressiveAssist() {
        return entityData.get(DATA_OPTION_AGGRESSIVE_ASSIST);
    }

    public void setOptionAggressiveAssist(boolean value) {
        entityData.set(DATA_OPTION_AGGRESSIVE_ASSIST, value);
    }

    public boolean optionStay() {
        return entityData.get(DATA_OPTION_STAY);
    }

    public void setOptionStay(boolean value) {
        entityData.set(DATA_OPTION_STAY, value);
    }

    /**
     * FIX: "it will also not stay when the stay is on" per explicit
     * direction - real gap, confirmed directly: optionStay() was
     * defined and settable from the bond screen, but nothing anywhere
     * ever actually READ it to gate any behavior at all. Overriding
     * the vanilla sit flag (rather than adding a separate, parallel
     * check everywhere) means every existing sit-gated behavior
     * automatically respects Stay too, with no risk of them drifting
     * out of sync later: SitWhenOrderedToGoal (stops ground movement,
     * triggers the same sit/sleep animation as sneak+use per your own
     * direction - "same one that happens when you shift+right click"),
     * and DragonFollowOwnerGoal's own canUse() check (already gated on
     * isOrderedToSit(), so Stay correctly overrides Following too,
     * even if both happen to be on at once).
     *
     * Honest limitation, not verified live: this handles the grounded
     * case cleanly through existing, already-correct sit machinery. The
     * airborne case ("if in the air, just fly in place") is less
     * certain - SitWhenOrderedToGoal claiming the move flag should stop
     * horizontal navigation-driven movement while still isFlying(), but
     * I haven't been able to confirm this actually produces a clean
     * hover rather than some other edge case (e.g. slowly settling) -
     * a real, separate thing to verify once you can see it in game
     * before assuming this piece specifically is fully solved.
     */
    @Override
    public boolean isOrderedToSit() {
        return super.isOrderedToSit() || optionStay();
    }

    /** Whether Join Fights ALSO proactively engages nearby hostile mobs without needing the owner to hit anything first. */
    public boolean attackNearbyHostile() {
        return entityData.get(DATA_ATTACK_NEARBY_HOSTILE);
    }

    public void setAttackNearbyHostile(boolean value) {
        entityData.set(DATA_ATTACK_NEARBY_HOSTILE, value);
    }

    public boolean attackNearbyNeutral() {
        return entityData.get(DATA_ATTACK_NEARBY_NEUTRAL);
    }

    public void setAttackNearbyNeutral(boolean value) {
        entityData.set(DATA_ATTACK_NEARBY_NEUTRAL, value);
    }

    public boolean attackNearbyPassive() {
        return entityData.get(DATA_ATTACK_NEARBY_PASSIVE);
    }

    public void setAttackNearbyPassive(boolean value) {
        entityData.set(DATA_ATTACK_NEARBY_PASSIVE, value);
    }

    public boolean useDragonStamina() {
        return entityData.get(DATA_USE_DRAGON_STAMINA);
    }

    public void setUseDragonStamina(boolean value) {
        entityData.set(DATA_USE_DRAGON_STAMINA, value);
    }

    /** true = dragon stamina drawn BEFORE the owner's own stamina bar; false = drawn AFTER (backup/last-resort). Only relevant while useDragonStamina() is true. */
    public boolean staminaBeforeOwn() {
        return entityData.get(DATA_STAMINA_BEFORE_OWN);
    }

    public void setStaminaBeforeOwn(boolean value) {
        entityData.set(DATA_STAMINA_BEFORE_OWN, value);
    }

    public int staminaLimiterPercent() {
        return entityData.get(DATA_STAMINA_LIMITER_PERCENT);
    }

    public void setStaminaLimiterPercent(int value) {
        entityData.set(DATA_STAMINA_LIMITER_PERCENT, Mth.clamp(value, 0, 100));
    }

    /** Delegates to the same real true-name system every other mob in this mod uses - a pure function of this dragon's own stable UUID. */
    public String trueName() {
        return com.dragonspeech.mind.TrueNameService.getOrCreateForMob(this).plaintext();
    }

    public boolean trueNameKnown() {
        return !isHatchling();
    }

    /** 0-100 display value for DragonBondScreen - derived directly from getAgeProgress() (already synced via DATA_AGE) rather than a separate synced field, since it's just a formatted view of data that's already consistent client-side. */
    public int growthPercent() {
        return Math.round(Mth.clamp(getAgeProgress(), 0f, 1f) * 100);
    }

    /** Vanilla's own getScale() defaults to a flat 1.0 regardless of actual size - overridden so anything reading it (DragonBondScreen's display, potentially other mods) sees the real, current age-based scale instead. */
    @Override
    public float getScale() {
        return getAgeScale();
    }

    @Override
    public boolean isTame() {
        return bondedOwner().isPresent();
    }

    @Override
    public boolean isOwnedBy(LivingEntity entity) {
        return entity instanceof Player player && isBondedTo(player);
    }

    public boolean isSaddled() {
        return entityData.get(DATA_SADDLED);
    }

    @Override
    public boolean isSaddleable() {
        // FIX: "for juvenile, I should not be able to put a saddle or
        // ride the dragon yet. Only past it" per explicit direction -
        // was !isHatchling(), which is true for BOTH juvenile AND
        // adult (isHatchling() only checks ageProgress < 0.5), so
        // juveniles could genuinely be saddled - the actual bug.
        return isAlive() && isAdult() && isTame();
    }

    @Override
    public void equipSaddle(ItemStack itemStack, @Nullable SoundSource soundSource) {
        setSaddled(true);
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.HORSE_SADDLE, getSoundSource(), 1, 1);
    }

    public void setSaddled(boolean saddled) {
        entityData.set(DATA_SADDLED, saddled);
    }

    public void addReproCount() {
        reproductionCount++;
    }

    public boolean canFly() {
        return !isHatchling();
    }

    public boolean shouldFly() {
        if (isFlying()) {
            return !onGround();
        }
        return canFly() && !isInWater() && !isNearGround();
    }

    public boolean isFlying() {
        return flying;
    }

    public void setFlying(boolean flying) {
        this.flying = flying;
    }

    public boolean isNearGround() {
        return nearGround;
    }

    public void setNavigation(boolean flying) {
        navigation = flying ? flyingNavigation : groundNavigation;
    }

    @Override
    public void tick() {
        super.tick();

        if (isServer()) {
            // Entity.move() normally clips against blocks, but a large flying dragon can still
            // be forced/teleported into a solid volume. Remember valid positions and recover
            // immediately instead of letting it remain embedded.
            if (level().noCollision(this, getBoundingBox().deflate(0.05))) {
                lastCollisionSafePos = position();
            } else if (lastCollisionSafePos != null) {
                setPos(lastCollisionSafePos.x, lastCollisionSafePos.y, lastCollisionSafePos.z);
                setDeltaMovement(Vec3.ZERO);
            } else {
                // Spawn/teleport edge case: if the first server tick begins embedded, find the
                // nearest vertical volume large enough for the full dragon before allowing AI
                // movement to continue. This avoids both suffocation and remaining trapped.
                AABB box = getBoundingBox().deflate(0.05);
                for (int dy = 1; dy <= 8; dy++) {
                    AABB up = box.move(0, dy, 0);
                    if (level().noCollision(this, up)) {
                        setPos(getX(), getY() + dy, getZ());
                        setDeltaMovement(Vec3.ZERO);
                        lastCollisionSafePos = position();
                        break;
                    }
                }
            }

            if (!isAdult() && tickCount % AGE_UPDATE_INTERVAL == 0) {
                entityData.set(DATA_AGE, age);
            }
            if (isAdult() && !isAncient()) {
                entityData.set(DATA_POST_ADULT_TICKS, postAdultTicks() + 1);
            }
            if (isAlive() && getRandom().nextFloat() < 0.001) {
                heal(1f);
            }
            regenDragonStamina();
        } else {
            if (animator instanceof ClientTickableAnimator tickable) {
                tickable.tick();
            }

            int age = getAge();
            if (age < 0) {
                setAge(++age);
            } else if (age > 0) {
                setAge(--age);
            }
        }

        nearGround = onGround() || !level().noCollision(this, new AABB(getX(), getY(), getZ(), getX(), getY() - (GROUND_CLEARENCE_THRESHOLD * getAgeScale()), getZ()));

        boolean flying = shouldFly();
        if (flying != isFlying()) {
            setFlying(flying);
            if (isServer()) {
                setNavigation(flying);
            }
        }

        updateAgeProgress();
    }

    @Override
    public void travel(Vec3 vec3) {
        if (isFlying()) {
            if (isControlledByLocalInstance()) {
                // FIX: "when I press only wasd, the dragon doesn't
                // move... I have to press z or space to actually start
                // moving" per explicit direction - real regression,
                // traced to the custom spherical-vector path added last
                // round. Removed that whole branch rather than keep
                // debugging it blind - it doubled as a second, separate
                // full movement model alongside this proven one, which
                // is real complexity/risk for what turned out to be a
                // one-line problem (see getRiddenInput()'s own doc for
                // the actual, safer fix: scaling the vector it already
                // returns, so this single path handles both normal
                // movement and the extreme-pitch case correctly).
                moveRelative(getSpeed(), vec3);
                move(MoverType.SELF, getDeltaMovement());
                if (getDeltaMovement().lengthSqr() < 0.1) {
                    setDeltaMovement(getDeltaMovement().add(0, Math.sin(tickCount / 4f) * 0.03, 0));
                }
                setDeltaMovement(getDeltaMovement().scale(0.9f));
            }

            calculateEntityAnimation(true);
        } else {
            super.travel(vec3);
        }
    }

    @Override
    protected Vec3 getRiddenInput(Player driver, Vec3 move) {
        double moveSideways = move.x;
        double moveY = move.y;
        double moveForward = Math.min(Math.abs(driver.zza) + Math.abs(driver.xxa), 1);

        if (isFlying() && hasLocalDriver()) {
            moveForward = moveForward > 0 ? moveForward : 0;

            boolean descendHeld = descendKeyProvider.getAsBoolean();
            boolean sprintHeld = sprintKeyProvider.getAsBoolean();

            if (((com.dragonspeech.mixin.LivingEntityJumpingAccessor) (Object) driver).dragonspeech$isJumping()) {
                moveY = 1;
            } else if (descendHeld) {
                moveY = -1;
            } else if (moveForward > 0) {
                // FIX: "when I press only wasd, the dragon doesn't
                // move" per explicit direction - real regression from
                // the previous round's separate custom movement path in
                // travel(), removed entirely rather than kept debugging
                // blind (see that method's own doc). Real, safer fix:
                // the same pitch-coupling Saints Dragons actually uses
                // (forwardY=-sin(pitch), horizontal scaled by cos(pitch)
                // - confirmed against their real source, not guessed)
                // built directly into the Vec3 this method already
                // returns, so the existing, already-proven
                // moveRelative() path in travel() handles both normal
                // WASD movement AND the extreme-pitch case correctly,
                // without a second, parallel movement system that can
                // drift out of sync with it. moveSideways is
                // deliberately left unscaled by pitch, matching Saints'
                // own real formula - strafing shouldn't become "more
                // vertical" just because you're looking up or down.
                double pitchRadians = Math.toRadians(-driver.getXRot());
                double forwardXZ = Math.cos(pitchRadians);
                moveY = Math.sin(pitchRadians) * moveForward;
                moveForward = moveForward * forwardXZ;
            }

            currentlySprintFlying = sprintHeld;
            currentlyDiving = descendHeld && moveY < 0;
        }

        var speed = getRiddenSpeed(driver);
        return new Vec3(moveSideways * speed, moveY * speed, moveForward * speed);
    }

    // Set within getRiddenInput() each tick (client-side, controlling
    // player only) and read immediately after by getRiddenSpeed() in
    // the same call chain - not synced separately, since the speed
    // they produce is what actually gets synced, via this entity's own
    // normal client-authoritative movement.
    private boolean currentlySprintFlying = false;
    private boolean currentlyDiving = false;

    @Override
    protected void tickRidden(Player driver, Vec3 move) {
        // FIX: "when I press no keys but look around with my mouse, the
        // dragon doesn't move at all. If I look around however, it
        // should make the dragon face the way I am looking" per
        // explicit direction - my previous fix here was too broad.
        // Re-reading against this clarification: orientation should
        // ALWAYS track the camera (even with no WASD held), only actual
        // POSITION movement needs to wait for WASD - I had gated both
        // together. Removed the gate entirely: setYRot() now always
        // tracks yHeadRot again. This doesn't bring back the original
        // "free-look silently steers the dragon" problem, since
        // moveRelative() (in travel()) only ever produces movement when
        // the input axes themselves are actually nonzero - orientation
        // updating freely doesn't cause movement on its own.
        float yaw = driver.yHeadRot;
        if (move.z > 0) {
            yaw += (float) Mth.atan2(driver.zza, driver.xxa) * (180f / (float) Math.PI) - 90;
        }
        yHeadRot = yaw;

        setXRot(driver.getXRot() * 1.1f);

        setYRot(Mth.rotateIfNecessary(yHeadRot, getYRot(), 12));

        if (isControlledByLocalInstance()) {
            if (!isFlying() && canFly() && ((com.dragonspeech.mixin.LivingEntityJumpingAccessor) (Object) driver).dragonspeech$isJumping()) {
                liftOff();
            }
        }
    }

    @Override
    protected float getRiddenSpeed(Player driver) {
        float base = (float) getAttributeValue(isFlying() ? FLYING_SPEED : MOVEMENT_SPEED);
        // "Its speed for flight should work like saints dragons" -
        // sprint boost while the sprint key is held, PLUS an additional
        // dive-boost specifically when descending via the descend key
        // at the same time ("the dragon goes faster when going down") -
        // stacks with the sprint boost rather than replacing it, for a
        // genuine "diving is the fastest way to move" feel. Both
        // multipliers are my own reasonable starting values, not given
        // exact numbers - easy to retune.
        if (isFlying() && currentlySprintFlying) {
            base *= 1.4f;
        }
        if (isFlying() && currentlyDiving) {
            base *= 1.3f;
        }
        return base;
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);

        var stackResult = stack.interactLivingEntity(player, this, hand);
        if (stackResult.consumesAction()) {
            return stackResult;
        }

        // FIX: no item-based taming branch at all - per explicit
        // direction, a wild-spawned dragon (never hatched from this
        // mod's own egg) can never be bonded, full stop. The original
        // here had an entire taming-item branch; deliberately removed,
        // not adapted.
        if (!isTame()) {
            return InteractionResult.PASS;
        }

        if (getHealthFraction() < 1 && isFoodItem(stack)) {
            var food = stack.get(net.minecraft.core.component.DataComponents.FOOD);
            if (food != null) {
                heal(food.nutrition());
                playSound(getEatingSound(stack), 0.7f, 1);
                stack.shrink(1);
                return InteractionResult.sidedSuccess(level().isClientSide);
            }
        }

        if (isBondedTo(player) && isSaddleable() && !isSaddled() && stack.getItem() instanceof SaddleItem) {
            stack.shrink(1);
            equipSaddle(stack, getSoundSource());
            return InteractionResult.sidedSuccess(level().isClientSide);
        }

        if (isBondedTo(player) && isSaddled() && stack.is(net.minecraft.world.item.Items.SHEARS)) {
            spawnAtLocation(Items.SADDLE);
            player.playSound(SoundEvents.SHEEP_SHEAR, 1f, 1f);
            setSaddled(false);
            gameEvent(GameEvent.SHEAR, player);
            stack.hurtAndBreak(1, player, getSlotForHand(hand));

            return InteractionResult.sidedSuccess(level().isClientSide);
        }

        if (isBondedTo(player) && (player.isSecondaryUseActive() || stack.is(Items.BONE))) {
            if (isServer()) {
                navigation.stop();
                setOrderedToSit(!isOrderedToSit());
                if (isOrderedToSit()) {
                    setTarget(null);
                }
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }

        if (isBondedTo(player) && isSaddled() && isAdult() && !isFood(stack)) {
            if (isServer()) {
                player.startRiding(this);
                navigation.stop();
                setTarget(null);
            }
            setOrderedToSit(false);
            setInSittingPose(false);
            return InteractionResult.sidedSuccess(level().isClientSide);
        }

        return super.mobInteract(player, hand);
    }

    public void liftOff() {
        if (canFly()) {
            jumpFromGround();
        }
    }

    @Override
    protected float getJumpPower() {
        return super.getJumpPower() * (canFly() ? 3 : 1);
    }

    @Override
    public boolean causeFallDamage(float pFallDistance, float pMultiplier, DamageSource pSource) {
        return !canFly() && super.causeFallDamage(pFallDistance, pMultiplier, pSource);
    }

    @Override
    protected void tickDeath() {
        ejectPassengers();

        setDeltaMovement(Vec3.ZERO);
        setYRot(yRotO);
        setYHeadRot(yHeadRotO);

        if (deathTime >= getMaxDeathTime()) {
            remove(RemovalReason.KILLED);
        }

        deathTime++;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ENDER_DRAGON_AMBIENT;
    }

    @Nullable
    @Override
    protected SoundEvent getHurtSound(DamageSource damageSourceIn) {
        return SoundEvents.ENDER_DRAGON_HURT;
    }

    public SoundEvent getStepSound() {
        return SoundEvents.ENDER_DRAGON_FLAP;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENDER_DRAGON_DEATH;
    }

    @Override
    public SoundEvent getEatingSound(ItemStack itemStackIn) {
        return SoundEvents.GENERIC_EAT;
    }

    public SoundEvent getAttackSound() {
        return SoundEvents.GENERIC_EAT;
    }

    public SoundEvent getWingsSound() {
        return SoundEvents.ENDER_DRAGON_FLAP;
    }

    @Override
    protected void playStepSound(BlockPos entityPos, BlockState state) {
        if (isInWater()) {
            return;
        }

        if (isHatchling()) {
            super.playStepSound(entityPos, state);
            return;
        }

        var soundType = state.getSoundType();
        if (level().getBlockState(entityPos.above()).getBlock() == Blocks.SNOW) {
            soundType = Blocks.SNOW.defaultBlockState().getSoundType();
        }

        playSound(getStepSound(), soundType.getVolume(), soundType.getPitch() * getVoicePitch());
    }

    @Override
    public void playAmbientSound() {
        if (getBreed() != null) {
            super.playAmbientSound();
        }
    }

    @Override
    public int getAmbientSoundInterval() {
        return 240;
    }

    @Override
    protected float getSoundVolume() {
        return getAgeScale();
    }

    @Override
    public float getVoicePitch() {
        return 2 - getAgeScale();
    }

    @Override
    protected Component getTypeName() {
        return super.getTypeName();
    }

    public boolean isFoodItem(ItemStack stack) {
        return stack.is(ItemTags.MEAT);
    }

    @Override
    public boolean isFood(ItemStack stack) {
        DragonBreed breed = getBreed();
        return breed != null && stack.is(net.minecraft.tags.ItemTags.MEAT);
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public boolean onClimbable() {
        return false;
    }

    @Override
    protected void dropEquipment() {
        super.dropEquipment();
        if (isSaddled()) {
            spawnAtLocation(Items.SADDLE);
        }
    }

    public void onWingsDown(float speed) {
        if (!isInWater()) {
            float pitch = (1 - speed);
            float volume = 0.3f + (1 - speed) * 0.2f;
            pitch *= getVoicePitch();
            volume *= getSoundVolume();
            level().playLocalSound(getX(), getY(), getZ(), getWingsSound(), SoundSource.VOICE, volume, pitch, true);
        }
    }

    @Override
    public void swing(InteractionHand hand) {
        playSound(getAttackSound(), 1, 0.7f);
        super.swing(hand);
    }

    @Override
    public boolean hurt(DamageSource src, float par2) {
        if (isInvulnerableTo(src)) {
            return false;
        }

        setOrderedToSit(false);

        // The current forest breed is Dragon Speech's earth-aligned dragon. Its passive
        // is raw durability rather than an elemental immunity. Addon breeds named "earth"
        // receive the same behavior automatically.
        ResourceLocation breedId = getBreedId();
        if (breedId != null && (breedId.getPath().equals("forest") || breedId.getPath().equals("earth"))) {
            par2 *= 0.75f;
        }
        return super.hurt(src, par2);
    }

    @Override
    public boolean canMate(Animal mate) {
        if (mate == this) {
            return false;
        }
        if (!(mate instanceof DragonEntity dragonMate)) {
            return false;
        }
        if (!canReproduce()) {
            return false;
        }
        if (!dragonMate.canReproduce()) {
            return false;
        }

        return isInLove() && mate.isInLove();
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean canReproduce() {
        if (!isTame() || getBreed() == null) {
            return false;
        }

        var limit = BASE_REPRO_LIMIT;
        return reproductionCount < limit || limit == -1;
    }

    @Override
    public void spawnChildFromBreeding(ServerLevel level, Animal animal) {
        // FIX (temporary): the original places a hatching egg block
        // here (HatchableEggBlock.place) and picks a cross-bred breed
        // (CrossBreedingManager). Neither is ported yet - this project
        // uses its OWN egg item, not theirs, so this needs its own
        // design (does breeding even make sense once only
        // egg-hatched dragons can be bonded? open question) rather
        // than a direct port. Left as a real no-op for now, not a
        // silently-wrong copy of logic that references classes that
        // don't exist in this project.
        addReproCount();
        if (animal instanceof DragonEntity mate) {
            mate.addReproCount();
        }
    }

    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob mob) {
        return null; // see spawnChildFromBreeding's own note - breeding's real behavior is still an open design question, not yet built.
    }

    @Override
    public boolean wantsToAttack(LivingEntity target, LivingEntity owner) {
        return !(target instanceof TamableAnimal tameable) || !Objects.equals(tameable.getOwner(), owner);
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return !isHatchling() && !hasControllingPassenger() && super.canAttack(target);
    }

    @Override
    public LivingEntity getControllingPassenger() {
        return getFirstPassenger() instanceof LivingEntity driver && isOwnedBy(driver) ? driver : null;
    }

    @Override
    protected void addPassenger(Entity passenger) {
        super.addPassenger(passenger);

        if (passenger instanceof Player) {
            passenger.setYRot(getYRot());
            passenger.setXRot(getXRot());
        }
        // FIX (temporary): DML's own MountControlsMessenger/
        // MountCameraManager client hooks aren't ported - no direct
        // replacement wired here yet.
    }

    @Override
    protected void positionRider(Entity ridden, MoveFunction pCallback) {
        ridden.xRotO = ridden.getXRot();
        ridden.yRotO = ridden.getYRot();
        ridden.setYBodyRot(yBodyRot);

        super.positionRider(ridden, pCallback);
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity ridden, EntityDimensions dimensions, float partialTick) {
        // FIX (reverting my own mistake per direct, empirical feedback -
        // not guessing a third direction blind): "you were completely
        // off... it needs to go the opposite way you went, and from the
        // original position I was before, only slightly moved forward."
        // My previous fix's reasoning (HEAD_OFS=-16 in the model's own
        // bone-space, therefore negative Z = forward here too) was
        // simply wrong - this attachment-point Vec3 is apparently in a
        // different coordinate convention than the model's own internal
        // bone-local space, and applying it put the rider at the tail,
        // the opposite of what was needed. Reverted to POSITIVE Z (the
        // direction that was previously reported as roughly correct -
        // "around the wings"), nudged only slightly further in that
        // same positive direction per "only slightly moved forward"
        // from that baseline - a small, conservative adjustment this
        // time, not another large swing.
        float scale = getAgeScale();
        return new Vec3(0, getBbHeight(), 1.5f * scale).yRot(-getYRot() * ((float) Math.PI / 180f));
    }

    @Override
    public boolean isInvulnerableTo(DamageSource src) {
        Entity srcEnt = src.getEntity();
        if (srcEnt != null && (srcEnt == this || hasPassenger(srcEnt))) {
            return true;
        }

        String damageId = src.typeHolder().unwrapKey().map(k -> k.location().toString()).orElse("");
        // Large dragons should never die merely because their body grazed a wall. Collision
        // recovery below also moves them back out, but this is the final safety net.
        if ("minecraft:in_wall".equals(damageId)) return true;

        ResourceLocation breedId = getBreedId();
        String breedPath = breedId == null ? "" : breedId.getPath();
        if ((breedPath.equals("fire")) && (damageId.equals("minecraft:on_fire") || damageId.equals("minecraft:in_fire")
                || damageId.equals("minecraft:lava") || damageId.equals("minecraft:hot_floor"))) return true;
        if (breedPath.equals("lightning") && damageId.equals("minecraft:lightning_bolt")) return true;
        if ((breedPath.equals("void") || breedPath.equals("end")) && damageId.equals("minecraft:out_of_world")) return true;
        if (breedPath.equals("ice") && (damageId.equals("minecraft:freeze") || damageId.equals("minecraft:drown"))) return true;

        DragonBreed breed = getBreed();
        if (breed != null && breed.immuneDamageTypes().contains(damageId)) {
            return true;
        }

        return super.isInvulnerableTo(src);
    }

    /** Elemental passive used by both player and NPC Dragon Speech spell pipelines. */
    public boolean isImmuneToElement(com.dragonspeech.engine.Element element) {
        ResourceLocation id = getBreedId();
        if (id == null || element == null) return false;
        return switch (id.getPath()) {
            case "fire" -> element == com.dragonspeech.engine.Element.FIRE;
            case "lightning" -> element == com.dragonspeech.engine.Element.LIGHTNING;
            case "ice" -> element == com.dragonspeech.engine.Element.ICE;
            case "void", "end" -> element == com.dragonspeech.engine.Element.VOID;
            default -> false;
        };
    }

    public float getHealthFraction() {
        return getHealth() / getMaxHealth();
    }

    public int getMaxDeathTime() {
        return 120;
    }

    @Override
    public void refreshDimensions() {
        double posXTmp = getX();
        double posYTmp = getY();
        double posZTmp = getZ();
        boolean onGroundTmp = onGround();

        super.refreshDimensions();

        setPos(posXTmp, posYTmp, posZTmp);

        setOnGround(onGroundTmp);
    }

    @Override
    protected EntityDimensions getDefaultDimensions(Pose pose) {
        return isInSittingPose() ? SITTING_DIMENSIONS.scale(getAgeScale()) : super.getDefaultDimensions(pose);
    }

    // ---------------------------------------------------------------- Elder / Ancient
    // "Elder and Ancient are growths that happen once the dragon
    // reaches adult... once the dragon reaches elder, it becomes 2x
    // longer growth time to reach ancient" per explicit direction -
    // genuinely new mechanic, not an extension of the existing
    // hatchling/juvenile/adult growth (that one caps hard at
    // ageProgress=1.0/age=0, and vanilla's own aging has no concept of
    // anything past that at all). Tracked via a separate counter
    // (postAdultTicks) rather than trying to push the existing age
    // field positive, since vanilla's own aiStep() doesn't define
    // behavior for that and I don't want this depending on undefined
    // vanilla behavior.
    //
    // Duration constants are my own reasonable defaults, not specified
    // anywhere - "takes longer than the before stages" and the 2x
    // elder-to-ancient ratio ARE specified, so kept exact; the actual
    // tick counts are easy to retune, just constants.
    public static final int ELDER_TICKS = 72000; // adult -> elder: 1 hour, 3x the default full hatchling->adult growth
    public static final int ANCIENT_TICKS = ELDER_TICKS * 2; // elder -> ancient: 2x the adult->elder duration, exactly as specified

    private static final float ELDER_SCALE = 1.15f;
    private static final float ANCIENT_SCALE = 1.35f;

    public int postAdultTicks() {
        return entityData.get(DATA_POST_ADULT_TICKS);
    }

    /** For the debug age command's direct use - syncs immediately, same reasoning as setAge()'s own override. */
    public void setPostAdultTicks(int ticks) {
        entityData.set(DATA_POST_ADULT_TICKS, Math.max(0, ticks));
    }

    public boolean isElder() {
        int t = postAdultTicks();
        return t >= ELDER_TICKS && t < ELDER_TICKS + ANCIENT_TICKS;
    }

    public boolean isAncient() {
        return postAdultTicks() >= ELDER_TICKS + ANCIENT_TICKS;
    }

    public float getAgeScale() {
        DragonBreed breed = getBreed();
        var mod = breed == null ? 1f : breed.sizeModifier();
        float base = (0.33f + (0.67f * getAgeProgress()));
        if (isAdult()) {
            int t = postAdultTicks();
            if (t < ELDER_TICKS) {
                // Adult -> Elder: smoothly interpolate 1.0 -> ELDER_SCALE.
                base = Mth.lerp((float) t / ELDER_TICKS, 1.0f, ELDER_SCALE);
            } else if (t < ELDER_TICKS + ANCIENT_TICKS) {
                // Elder -> Ancient: smoothly interpolate ELDER_SCALE -> ANCIENT_SCALE.
                float progress = (float) (t - ELDER_TICKS) / ANCIENT_TICKS;
                base = Mth.lerp(progress, ELDER_SCALE, ANCIENT_SCALE);
            } else {
                base = ANCIENT_SCALE;
            }
        }
        return base * mod;
    }

    @Override
    public int getAge() {
        return age;
    }

    /**
     * FIX: "the dragon doesn't seem to age over juvenile" using the
     * debug age command - real cause, confirmed by tracing the actual
     * code rather than guessed: setAge() was inherited from vanilla
     * completely unmodified, which just sets the raw protected `age`
     * field directly. The entityData sync that actually drives visual
     * size and isAdult()/isJuvenile() only happened via tick()'s own
     * throttled, conditional check (every AGE_UPDATE_INTERVAL=100
     * ticks, and only while `!isAdult()` at that moment) - a
     * reasonable throttle for natural, gradual vanilla-driven growth
     * (which manipulates the raw field directly too, never through
     * setAge() at all, so this override doesn't affect that path), but
     * wrong for a direct setAge() call that expects to take effect
     * immediately - could sit stale for up to 5 seconds, or even
     * indefinitely once isAdult() happened to already read true from
     * whatever the last synced state was.
     */
    @Override
    public void setAge(int age) {
        super.setAge(age);
        entityData.set(DATA_AGE, age);
    }

    public void updateAgeProgress() {
        DragonBreed breed = getBreed();
        float growth = -(breed == null ? DragonBreed.DEFAULT_GROWTH_TICKS : breed.growthTicks());
        float min = Math.min(getAge(), 0);
        ageProgress = 1 - (min / growth);
    }

    public float getAgeProgress() {
        return ageProgress;
    }

    private void updateAgeProperties() {
        setAge(entityData.get(DATA_AGE));
        updateAgeProgress();
        refreshDimensions();

        getAttribute(STEP_HEIGHT).setBaseValue(Math.max(2 * getAgeProgress(), 1));

        if (isServer()) {
            // Breed passives are applied as base attributes so growth scaling still layers on top.
            double baseHealth = BASE_HEALTH;
            double baseArmor = 0.0;
            double baseMove = BASE_SPEED_GROUND;
            double baseFly = BASE_SPEED_FLYING;
            ResourceLocation breedId = getBreedId();
            String path = breedId == null ? "" : breedId.getPath();
            if (path.equals("forest") || path.equals("earth")) { baseHealth = 90.0; baseArmor = 10.0; }
            else if (path.equals("end")) { baseHealth = 75.0; }
            else if (path.equals("lightning")) { baseMove = 0.28; }
            else if (path.equals("gold")) { baseFly = 0.392; }
            getAttribute(MAX_HEALTH).setBaseValue(baseHealth);
            getAttribute(ARMOR).setBaseValue(baseArmor);
            getAttribute(MOVEMENT_SPEED).setBaseValue(baseMove);
            getAttribute(FLYING_SPEED).setBaseValue(baseFly);

            var healthFrac = getHealthFraction();

            double modValue = -(1d - Math.max(getAgeProgress(), 0.1));
            var mod = new AttributeModifier(AGE_ATTR_MODIFIER_ID, modValue, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);

            getAttribute(MAX_HEALTH).addOrReplacePermanentModifier(mod);
            getAttribute(ATTACK_DAMAGE).addOrUpdateTransientModifier(mod);

            setHealth(healthFrac * getMaxHealth());
        }
    }

    public boolean isHatchling() {
        return getAgeProgress() < 0.5f;
    }

    public boolean isJuvenile() {
        return getAgeProgress() >= 0.5f && getAgeProgress() < 1f;
    }

    public boolean isAdult() {
        return getAgeProgress() >= 1f;
    }

    @Override
    public boolean isBaby() {
        return !isAdult();
    }

    @Override
    public void setBaby(boolean baby) {
        DragonBreed breed = getBreed();
        var growth = -(breed == null ? DragonBreed.DEFAULT_GROWTH_TICKS : breed.growthTicks());
        setAge(baby ? growth : 0);
        entityData.set(DATA_AGE, age);
    }

    @Override
    public void ageUp(int p_146741_, boolean p_146742_) {
        super.ageUp(p_146741_, p_146742_);
        entityData.set(DATA_AGE, getAge());
    }

    public boolean isServer() {
        return !level().isClientSide;
    }

    /** Returns Object, not DragonAnimator - see animatorFactory's own doc for why. Client-only callers (DragonModel) cast this back. */
    public Object getAnimator() {
        return animator;
    }

    @Override
    public boolean fireImmune() {
        if (super.fireImmune()) {
            return true;
        }
        DragonBreed breed = getBreed();
        return breed != null && breed.immuneDamageTypes().contains("minecraft:on_fire");
    }

    @Override
    public boolean isInWall() {
        // Collision recovery in tick() keeps the body out of solids. Returning false prevents
        // vanilla's small-entity suffocation heuristic from punishing a dragon whose large
        // bounding box only brushes a wall for a frame.
        return false;
    }

    @Override
    public Vec3 getLightProbePosition(float p_20309_) {
        return new Vec3(getX(), getY() + getBbHeight(), getZ());
    }

    public boolean hasLocalDriver() {
        return getControllingPassenger() instanceof Player p && p.isLocalPlayer();
    }
}
