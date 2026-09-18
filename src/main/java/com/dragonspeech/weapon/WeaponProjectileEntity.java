package com.dragonspeech.weapon;

import com.dragonspeech.engine.MagicAffinity;
import com.dragonspeech.entity.MagicBarrierEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;

import java.util.Locale;
import java.util.UUID;

/**
 * A real, flying, sticking projectile for the hurled-weapon system - a
 * sword/axe/pick/shovel/hoe/trident thrown as a genuine physics-simulated
 * entity rather than the instant-hit-plus-visual pattern
 * BlockThrowEffectHandler uses for hurled earth. This is deliberately
 * built on top of AbstractArrow (the same base vanilla's own Trident
 * uses) rather than hand-rolled, so flight, gravity, block/entity
 * collision, sticking-in-a-surface, and pickup-by-walking-over-it all
 * come from the same proven, decades-stable vanilla code path a real
 * arrow uses - this class only supplies WHAT it looks like when picked
 * up (getDefaultPickupItem()) and HOW HARD it hits (setBaseDamage(),
 * called by whoever spawns one - see HurlWeaponEffectHandler).
 *
 * CONFIRMED AGAINST A REAL 1.21.1 BUILD:
 *   - The abstract method to implement is getDefaultPickupItem(), not
 *     getPickupItem() (which still exists, but as a different, already-
 *     implemented method built on top of this one - only override the
 *     "default" one).
 *   - `pickup` (Pickup.ALLOWED/DISALLOWED) is a plain inherited field,
 *     not a setPickup(...) method - assign it directly.
 *   - There is no setKnockback(int) method; AbstractArrow's own
 *     knockback already defaults to 0, so nothing needs setting there.
 *
 * CONSTRUCTOR-ORDER BUG (fixed previously): AbstractArrow's own bare
 * (EntityType, Level) constructor calls this.getDefaultPickupItem() on
 * itself DURING super(...), before this class's own field initializers
 * have run, so getDefaultPickupItem() tolerates material/toolType being
 * transiently null at that exact instant.
 *
 * NETWORK-SYNC BUG (this is why every projectile visually rendered as a
 * wooden sword no matter what material/tool was actually spoken, even
 * though damage and the real pickup item were always correct):
 * `material`/`toolType` were plain Java fields, set on the SERVER's
 * entity instance inside the gameplay constructor - but that constructor
 * NEVER RUNS on the client. The client only ever builds its own copy of
 * this entity via the bare (EntityType, Level) registry constructor when
 * the spawn packet arrives, so its material/toolType stayed at their
 * field-initializer defaults (WOOD/SWORD) forever, no matter what the
 * server-side entity actually had. Since WeaponProjectileRenderer runs
 * entirely client-side and reads entity.getItem() off the CLIENT's copy,
 * it was always drawing the wood-sword fallback - the server-side damage
 * calculation and the real pickup item were both unaffected by this the
 * whole time, since those run server-side off the correctly-set fields.
 * Fixed by tracking material/toolType as SynchedEntityData (the exact
 * same mechanism AbstractArrow's own ID_FLAGS/PIERCE_LEVEL and Arrow's
 * ID_EFFECT_COLOR use to get server-set values onto the client) instead
 * of plain fields - vanilla automatically syncs these on spawn and on
 * change, which plain fields never do.
 */
public class WeaponProjectileEntity extends AbstractArrow implements ItemSupplier {

    private static final EntityDataAccessor<Integer> DATA_MATERIAL =
        SynchedEntityData.defineId(WeaponProjectileEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_TOOL_TYPE =
        SynchedEntityData.defineId(WeaponProjectileEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_AFFINITY =
        SynchedEntityData.defineId(WeaponProjectileEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_CONJURED =
        SynchedEntityData.defineId(WeaponProjectileEntity.class, EntityDataSerializers.BOOLEAN);

    /** Brief server-only immunity to the barrier just struck, preventing a reflected projectile
     * from immediately touching the same mathematical barrier volume again on the next tick. */
    private UUID lastBarrierHit;
    private int barrierRehitCooldown;

    /** Registry constructor - required by EntityType.Builder, also used by /summon, when loading a saved entity back from NBT, and (critically) by the CLIENT when a spawn packet arrives - see class doc's NETWORK-SYNC BUG note. */
    public WeaponProjectileEntity(EntityType<? extends WeaponProjectileEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_MATERIAL, ToolMaterial.WOOD.ordinal());
        builder.define(DATA_TOOL_TYPE, ToolType.SWORD.ordinal());
        builder.define(DATA_AFFINITY, MagicAffinity.ARCANE.ordinal());
        builder.define(DATA_CONJURED, true);
    }

    public ToolMaterial material() {
        return safeOrdinal(ToolMaterial.values(), this.entityData.get(DATA_MATERIAL), ToolMaterial.WOOD);
    }

    public ToolType toolType() {
        return safeOrdinal(ToolType.values(), this.entityData.get(DATA_TOOL_TYPE), ToolType.SWORD);
    }

    public MagicAffinity magicAffinity() {
        return safeOrdinal(MagicAffinity.values(), this.entityData.get(DATA_AFFINITY), MagicAffinity.ARCANE);
    }

    public boolean isConjured() {
        return this.entityData.get(DATA_CONJURED);
    }

    public boolean isMagicalConstruct() {
        return isConjured() && magicAffinity() != MagicAffinity.ARCANE;
    }

    private static <T> T safeOrdinal(T[] values, int ordinal, T fallback) {
        return (ordinal >= 0 && ordinal < values.length) ? values[ordinal] : fallback;
    }

    /**
     * Spawns a live hurled weapon. Call {@link #shoot(double, double, double, float, float)}
     * immediately after construction to actually launch it - this
     * constructor only positions and arms the entity.
     *
     * @param drawnFromInventory the REAL item this weapon was drawn from
     *        (via the "taka" word finding a matching item already in the
     *        caster's inventory - see HurlWeaponEffectHandler), or null
     *        if this weapon was conjured from nothing instead. A drawn
     *        weapon keeps its actual durability/enchantments and can be
     *        picked back up afterward, exactly like it never left your
     *        hand; a conjured one is a temporary construct - it deals
     *        the same damage, but it unravels rather than leaving
     *        anything behind, so pickup is disabled for it.
     */
    public WeaponProjectileEntity(Level level, LivingEntity owner, ToolMaterial material, ToolType toolType, float damage, ItemStack drawnFromInventory) {
        this(level, owner, material, toolType, damage, drawnFromInventory, MagicAffinity.ARCANE);
    }

    public WeaponProjectileEntity(Level level, LivingEntity owner, ToolMaterial material, ToolType toolType, float damage, ItemStack drawnFromInventory, MagicAffinity affinity) {
        super(com.dragonspeech.entity.DragonSpeechEntities.WEAPON_PROJECTILE, level);
        this.entityData.set(DATA_MATERIAL, material.ordinal());
        this.entityData.set(DATA_TOOL_TYPE, toolType.ordinal());
        this.entityData.set(DATA_AFFINITY, (affinity == null ? MagicAffinity.ARCANE : affinity).ordinal());
        this.setOwner(owner);
        this.setPos(owner.getX(), owner.getEyeY() - 0.1, owner.getZ());
        this.setBaseDamage(damage);
        boolean drawn = drawnFromInventory != null && !drawnFromInventory.isEmpty();
        this.entityData.set(DATA_CONJURED, !drawn);
        // `pickup` is a plain inherited field on AbstractArrow (no
        // setter method exists), so it's set directly here. Only a
        // weapon actually drawn from the caster's own inventory can be
        // recovered afterward - see the parameter doc above.
        this.pickup = drawn ? AbstractArrow.Pickup.ALLOWED : AbstractArrow.Pickup.DISALLOWED;
        // Replace AbstractArrow's cached default (a wood sword, computed
        // before the entityData above was set - see class doc) with the
        // REAL item now that we actually know it: either the exact stack
        // drawn from inventory (preserving its durability/enchantments)
        // or, for a conjured throw, a fresh generic stack used only for
        // display since pickup is disallowed anyway. setPickupItemStack
        // is the same protected setter AbstractArrow's own subclasses
        // use for exactly this kind of "the real item wasn't known yet
        // at construction time" fixup - this part only matters
        // server-side (drop-on-pickup uses it), the client-visible model
        // comes from getItem()/entityData below.
        this.setPickupItemStack(drawn ? drawnFromInventory.copy() : WeaponItems.stackFor(material, toolType));
        // No enchantment-style extra knockback beyond the impact itself -
        // AbstractArrow's own knockback already defaults to 0, so there
        // is nothing to set here.
        // A thrown tool has real heft - let it drop out of the air over
        // distance and time out like any other stuck projectile rather
        // than lingering forever.
        this.setNoGravity(false);
    }

    @Override
    public void tick() {
        // Magic barriers intentionally do not participate in vanilla collision because players are
        // allowed to cross them. Hurled magical/real weapon projectiles therefore need their own
        // swept-path barrier test BEFORE AbstractArrow advances the projectile this tick.
        if (!level().isClientSide() && !isRemoved()) {
            if (barrierRehitCooldown > 0) {
                barrierRehitCooldown--;
                if (barrierRehitCooldown == 0) lastBarrierHit = null;
            }
            if (level() instanceof ServerLevel server && interceptBarrier(server)) {
                return;
            }
        }
        super.tick();
    }

    /** @return true when the projectile was absorbed/reflected and vanilla movement must stop now. */
    private boolean interceptBarrier(ServerLevel level) {
        Vec3 velocity = getDeltaMovement();
        if (velocity.lengthSqr() < 1.0e-8) return false;
        Vec3 start = position();
        Vec3 end = start.add(velocity);
        AABB search = new AABB(start, end).inflate(1.0);

        MagicBarrierEntity best = null;
        Vec3 bestHit = null;
        double bestAlong = Double.MAX_VALUE;
        UUID ownerId = getOwner() == null ? null : getOwner().getUUID();
        for (MagicBarrierEntity barrier : level.getEntitiesOfClass(MagicBarrierEntity.class, search, b -> {
            if (b.isRemoved()) return false;
            if (lastBarrierHit != null && lastBarrierHit.equals(b.getUUID())) return false;
            return ownerId == null || b.casterId() == null || !ownerId.equals(b.casterId());
        })) {
            Vec3 hit = firstAabbHit(start, end, barrier.getBoundingBox().inflate(0.10));
            if (hit == null) continue;
            double along = start.distanceToSqr(hit);
            if (along < bestAlong) {
                bestAlong = along;
                best = barrier;
                bestHit = hit;
            }
        }
        if (best == null) return false;

        MagicAffinity affinity = magicAffinity();
        float power = (float) Math.max(1.0, getBaseDamage());
        boolean survives = best.absorbSpellImpact(power,
            affinity.element().map(java.util.List::of).orElseGet(java.util.List::of), bestHit);

        if (!survives) {
            // Barrier broke: the projectile continues through with reduced force.
            setBaseDamage(Math.max(1.0, getBaseDamage() * 0.55));
            setDeltaMovement(velocity.scale(0.72));
            Vec3 dir = velocity.normalize();
            setPos(bestHit.add(dir.scale(0.22)));
            lastBarrierHit = best.getUUID();
            barrierRehitCooldown = 3;
            return false;
        }

        if (best.reflective()) {
            Vec3 reflected = velocity.scale(-0.82);
            UUID barrierCaster = best.casterId();
            if (barrierCaster != null) {
                ServerPlayer reflector = level.getServer().getPlayerList().getPlayer(barrierCaster);
                if (reflector != null) setOwner(reflector);
            }
            setDeltaMovement(reflected);
            Vec3 away = reflected.lengthSqr() < 1.0e-8 ? new Vec3(0, 0.1, 0) : reflected.normalize();
            setPos(bestHit.add(away.scale(0.32)));
            lastBarrierHit = best.getUUID();
            barrierRehitCooldown = 4;
            return true;
        }

        if (isConjured()) {
            // A conjured construct has no persistent matter to recover; a barrier that fully
            // defeats it simply unravels the construct.
            discard();
        } else {
            // A real weapon spoken with `taka` must not be deleted by a magical wall. Kill most of
            // its forward momentum and let gravity drop it away from the barrier so it remains
            // recoverable through AbstractArrow's normal pickup path.
            Vec3 bounced = velocity.scale(-0.10).add(0, -0.08, 0);
            setDeltaMovement(bounced);
            Vec3 away = velocity.normalize().scale(-0.26);
            setPos(bestHit.add(away));
            lastBarrierHit = best.getUUID();
            barrierRehitCooldown = 4;
        }
        return true;
    }

    /** First intersection of a finite segment with an AABB, or null. */
    private static Vec3 firstAabbHit(Vec3 start, Vec3 end, AABB box) {
        Vec3 d = end.subtract(start);
        double tMin = 0.0, tMax = 1.0;
        double[] s = {start.x, start.y, start.z};
        double[] v = {d.x, d.y, d.z};
        double[] min = {box.minX, box.minY, box.minZ};
        double[] max = {box.maxX, box.maxY, box.maxZ};
        for (int i = 0; i < 3; i++) {
            if (Math.abs(v[i]) < 1.0e-9) {
                if (s[i] < min[i] || s[i] > max[i]) return null;
                continue;
            }
            double a = (min[i] - s[i]) / v[i];
            double b = (max[i] - s[i]) / v[i];
            if (a > b) { double q = a; a = b; b = q; }
            tMin = Math.max(tMin, a);
            tMax = Math.min(tMax, b);
            if (tMin > tMax) return null;
        }
        return start.add(d.scale(Math.max(0, Math.min(1, tMin))));
    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        // Called by AbstractArrow's own bare-constructor DURING
        // super(...), before defineSynchedData has necessarily run its
        // course either - material()/toolType() above already fall back
        // safely to WOOD/SWORD for any out-of-range ordinal, so this is
        // safe to call at any point in the lifecycle, client or server.
        return WeaponItems.stackFor(material(), toolType());
    }

    /**
     * ItemSupplier's contract - lets the client render this entity as
     * its real item via WeaponProjectileRenderer. Now correctly reflects
     * the actual spoken material/tool on the CLIENT too, since
     * material()/toolType() read from entityData (networked) rather than
     * plain fields (server-only) - see class doc's NETWORK-SYNC BUG note.
     */
    @Override
    public ItemStack getItem() {
        return getDefaultPickupItem();
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        Entity target = hit.getEntity();
        MagicAffinity affinity = magicAffinity();

        if (target instanceof MagicBarrierEntity barrier) {
            float power = (float) Math.max(1.0, getBaseDamage());
            boolean survives = barrier.absorbSpellImpact(power,
                affinity.element().map(java.util.List::of).orElseGet(java.util.List::of), hit.getLocation());
            if (survives) {
                discard();
            } else {
                // A construct that actually breaks the barrier keeps going, but loses much of its
                // force instead of teleporting through at full power.
                setBaseDamage(Math.max(1.0, getBaseDamage() * 0.55));
                setDeltaMovement(getDeltaMovement().scale(0.72));
                Vec3 v = getDeltaMovement();
                if (v.lengthSqr() > 1.0e-8) setPos(position().add(v.normalize().scale(0.25)));
            }
            return;
        }

        super.onHitEntity(hit);
        if (!level().isClientSide() && affinity != MagicAffinity.ARCANE
            && getOwner() instanceof ServerPlayer caster && target instanceof LivingEntity living) {
            affinity.applyConstructHit(caster, living, (float) Math.max(1.0, getBaseDamage() * 0.32));
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("ToolMaterial", material().getSerializedName());
        tag.putString("ToolType", toolType().getSerializedName());
        tag.putString("MagicAffinity", magicAffinity().getSerializedName());
        tag.putBoolean("Conjured", isConjured());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("ToolMaterial")) {
            try {
                this.entityData.set(DATA_MATERIAL, ToolMaterial.valueOf(tag.getString("ToolMaterial").toUpperCase(Locale.ROOT)).ordinal());
            } catch (IllegalArgumentException ignored) {
                this.entityData.set(DATA_MATERIAL, ToolMaterial.WOOD.ordinal());
            }
        }
        if (tag.contains("ToolType")) {
            try {
                this.entityData.set(DATA_TOOL_TYPE, ToolType.valueOf(tag.getString("ToolType").toUpperCase(Locale.ROOT)).ordinal());
            } catch (IllegalArgumentException ignored) {
                this.entityData.set(DATA_TOOL_TYPE, ToolType.SWORD.ordinal());
            }
        }

        if (tag.contains("MagicAffinity")) {
            try {
                this.entityData.set(DATA_AFFINITY, MagicAffinity.valueOf(tag.getString("MagicAffinity").toUpperCase(Locale.ROOT)).ordinal());
            } catch (IllegalArgumentException ignored) {
                this.entityData.set(DATA_AFFINITY, MagicAffinity.ARCANE.ordinal());
            }
        }
        if (tag.contains("Conjured")) {
            this.entityData.set(DATA_CONJURED, tag.getBoolean("Conjured"));
        }
    }
}
