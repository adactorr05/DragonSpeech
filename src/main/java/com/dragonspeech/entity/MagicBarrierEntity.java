package com.dragonspeech.entity;

import com.dragonspeech.DragonSpeech;
import com.dragonspeech.engine.MagicAffinity;
import com.dragonspeech.spell.SustainMode;
import com.dragonspeech.fx.DragonSpeechParticles;
import com.dragonspeech.fx.SpellFx;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * The physical body of "skjoldr". A real entity with a real bounding box,
 * unlike verja's invisible binding - see BarrierProtection for the
 * damage-redirect half and ShieldShellParticle for the visible half.
 *
 * MOVEMENT: players always pass through freely, in and out, through any
 * shield. Every other living entity is repelled the instant it tries to
 * cross INTO the volume from outside - see enforceExclusion(). This is a
 * one-way gate, not a cage: anything already inside is always free to
 * leave. It's an ACTIVE per-tick position check, not vanilla's own
 * collision system (canBeCollidedWith() is a single yes/no flag with no
 * way to ask WHO is colliding, which is exactly what "players pass,
 * everything else doesn't" needs) - see canBeCollidedWith()'s own
 * comment for why it's now always false.
 *
 * TWO LIFECYCLES:
 *  - PERSONAL (ground=false): follows the caster, expires after a
 *    power-scaled duration, never saved to disk - the original skjoldr
 *    behavior, unchanged.
 *  - GROUND (ground=true, "skjoldr ristmark"): planted at a fixed point,
 *    does NOT expire from age, DOES get saved across a server restart
 *    (addAdditionalSaveData/readAdditionalSaveData actually serialize
 *    state now, unlike the personal case), and can be grown or topped up
 *    later by feed() - see BarrierEffectHandler for how re-casting
 *    "skjoldr ristmark afla" near an existing one finds and feeds it
 *    instead of creating a second one on top of it.
 *
 * VERSION-RISK NOTE: isPickable() and the single-arg hurt(DamageSource,
 * float) signature remain the parts of this class most likely to have
 * shifted in your exact 1.21.1 mappings. teleportTo()/setDeltaMovement()
 * (used by enforceExclusion/repel) are long-stable, low-risk vanilla
 * Entity methods - not a version concern the way the damage-handling
 * signature is.
 */
public class MagicBarrierEntity extends Entity {

    private static final EntityDataAccessor<Float> DATA_RADIUS =
            SynchedEntityData.defineId(MagicBarrierEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_SHAPE =
            SynchedEntityData.defineId(MagicBarrierEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_CAGE =
            SynchedEntityData.defineId(MagicBarrierEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_COLOR =
            SynchedEntityData.defineId(MagicBarrierEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_FADE_COLOR =
            SynchedEntityData.defineId(MagicBarrierEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_FACING_YAW =
            SynchedEntityData.defineId(MagicBarrierEntity.class, EntityDataSerializers.FLOAT);

    private static final float WALL_THICKNESS = 0.5f;
    private static final int FX_INTERVAL_TICKS = 8; // dense accent sparkles marking the boundary - see idleAccentFx
    /** Ground shields can be grown far larger than a personal bubble - a village dome is the whole point. */
    public static final double GROUND_MAX_RADIUS = 48.0;
    public static final double PERSONAL_MAX_RADIUS = 6.0;
    /** How close a "skjoldr ristmark" cast has to land to an existing ground shield to feed it instead of planting a new one. */
    public static final double FEED_SEARCH_RADIUS = 6.0;

    // EBW-style saturated shield blue - the actual fix for "not visible enough". Public so BarrierEffectHandler's element-absent fallback stays in sync with this instead of duplicating the value.
    public static final int DEFAULT_COLOR = 0x2f8fff;
    public static final int DEFAULT_FADE = 0xbfe8ff;
    // "bur" (cage) gets its own default colour when no element is named - a warm orange/red instead of ward-blue, so a cage reads as visually distinct (and a little more ominous) at a glance rather than looking like an inside-out version of the same shield.
    public static final int CAGE_DEFAULT_COLOR = 0xff6a1f;
    public static final int CAGE_DEFAULT_FADE = 0xffd9a8;

    private UUID casterId;
    /** Who a personal CAGE is actually centred on/following - the entity the caster targeted, NOT the caster themselves (a cage traps something else; see BarrierEffectHandler.resolveCageTarget()). Null for wards (which still follow the caster) and for ground shields (which don't follow anything). */
    private UUID targetId;
    /** "aflbinda" - see absorbDamage(): when true, hits drain the CASTER's stamina instead of this shield's own strength pool. */
    private boolean aflbound;
    /** Authoritative sustain semantics. aflbound remains serialized for old-world compatibility. */
    private SustainMode sustainMode = SustainMode.DURATION;
    /** sveigja: surviving impacts are bent back/away instead of only being absorbed. */
    private boolean reflective;
    private MagicAffinity affinity = MagicAffinity.ARCANE; // semantic material/affinity of the barrier
    private int colorOverride = DEFAULT_COLOR;
    private int fadeOverride = DEFAULT_FADE;
    private float strength;
    private float maxStrength;
    private int lifetimeTicks;
    private boolean ground;
    /** True for a placed but temporary barrier (varnbinda). Unlike ground=true it is not saved forever, but it also must not follow the caster. */
    private boolean stationary;
    private boolean shellSpawned;

    public MagicBarrierEntity(EntityType<? extends MagicBarrierEntity> type, Level level) {
        super(type, level);
        this.noPhysics = false;
        this.blocksBuilding = false;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_RADIUS, 1.5f);
        builder.define(DATA_SHAPE, BarrierShape.SPHERE.ordinal());
        builder.define(DATA_CAGE, false);
        builder.define(DATA_COLOR, DEFAULT_COLOR);
        builder.define(DATA_FADE_COLOR, DEFAULT_FADE);
        builder.define(DATA_FACING_YAW, 0f);
    }

    /** Called once, immediately after spawning, by BarrierEffectHandler. */
    public void configure(ServerPlayer caster, double radius, BarrierShape shape, boolean cage, float facingYaw,
                          MagicAffinity affinity, int color, int fadeColor, float strength, int lifetimeTicks,
                          boolean ground, boolean stationary, LivingEntity cageTarget, boolean aflbound, SustainMode sustainMode) {
        this.casterId = caster.getUUID();
        this.targetId = cageTarget != null ? cageTarget.getUUID() : null;
        this.sustainMode = sustainMode == null ? (aflbound ? SustainMode.CASTER : SustainMode.DURATION) : sustainMode;
        this.aflbound = this.sustainMode == SustainMode.CASTER;
        this.entityData.set(DATA_RADIUS, (float) radius);
        this.entityData.set(DATA_SHAPE, shape.ordinal());
        this.entityData.set(DATA_CAGE, cage);
        this.entityData.set(DATA_FACING_YAW, facingYaw);
        this.affinity = affinity == null ? MagicAffinity.ARCANE : affinity;
        this.colorOverride = color;
        this.fadeOverride = fadeColor;
        // The CLIENT-visible color: resolved once here (element wins if
        // present, else the explicit override) and pushed through
        // entityData so the renderer - which runs on a completely
        // separate client-side copy of this entity that never sees
        // colorOverride/fadeOverride/element directly - actually gets
        // it. This was the whole "cage renders blue instead of orange"
        // bug: those three fields were plain Java fields, set only on
        // the SERVER's instance inside configure() (which never runs on
        // the client), so the renderer was always reading its own
        // never-updated field-initializer defaults. Server-side particle
        // bursts (SpellFx.burst(..., color(), ...)) never showed this
        // bug because they run on the server, reading the server's own
        // correctly-configured instance, and bake the resolved RGB
        // straight into the outgoing packet - exactly why the particles
        // were already the right color while the shell wasn't.
        int resolvedColor = this.affinity == MagicAffinity.ARCANE ? color : this.affinity.color();
        int resolvedFade = this.affinity == MagicAffinity.ARCANE ? fadeColor : this.affinity.fadeColor();
        this.entityData.set(DATA_COLOR, resolvedColor);
        this.entityData.set(DATA_FADE_COLOR, resolvedFade);
        this.strength = strength;
        this.maxStrength = strength;
        this.lifetimeTicks = lifetimeTicks;
        this.ground = ground;
        this.stationary = stationary || ground;
        this.setBoundingBox(computeBoundingBox());
        // NOTE: deliberately NOT spawning the shell particle here. This
        // method runs BEFORE addFreshEntity() has added this entity to
        // the level - no client knows this entity exists yet, so a
        // particle linked to it here would resolve setEntity(null)
        // permanently (see ClientParticleSpawner: entity lookup happens
        // once, synchronously, with no retry). tick() spawns it instead,
        // once this entity is actually tracked - see spawnShellParticleIfNeeded().
    }

    /** "skjoldr ristmark afla [mikla/litla]" on an existing ground shield: adds stored energy and/or grows or shrinks its radius. Anyone can feed a ground shield, not just whoever planted it - a village shield is meant to be communal. */
    public void feed(float bonusEnergy, double radiusDelta) {
        if (bonusEnergy > 0f) {
            this.sustainMode = SustainMode.RESERVE;
            this.aflbound = false;
        }
        this.strength = Math.max(1f, this.strength + bonusEnergy);
        this.maxStrength = Math.max(this.maxStrength, this.strength);

        if (radiusDelta != 0) {
            double newRadius = Math.max(1.0, Math.min(GROUND_MAX_RADIUS, radius() + radiusDelta));
            this.entityData.set(DATA_RADIUS, (float) newRadius);
        }
        this.setBoundingBox(computeBoundingBox());

        if (level() instanceof ServerLevel level) {
            SpellFx.flash(level, color(), position());
            SpellFx.burst(level, DragonSpeechParticles.SPARKLE, color(), fadeColor(), position(), 16, 0.12);
        }
    }

    /** "letta skjoldr" - a clean, deliberate dispel rather than just discard(): still flashes/bursts like any other shield-down event so it's obvious something happened, but skips the "Your shield shatters" message (that phrasing is for being BROKEN, not willingly released). */
    public void dispel() {
        if (level() instanceof ServerLevel level) {
            SpellFx.flash(level, color(), position());
            SpellFx.burst(level, DragonSpeechParticles.SPARKLE, color(), fadeColor(), position(), 14, 0.15);
        }
        discard();
    }

    public double radius() {
        return this.entityData.get(DATA_RADIUS);
    }

    public BarrierShape shape() {
        int ordinal = this.entityData.get(DATA_SHAPE);
        BarrierShape[] values = BarrierShape.values();
        return (ordinal >= 0 && ordinal < values.length) ? values[ordinal] : BarrierShape.SPHERE;
    }

    /** Convenience for the wall-specific math (computeBoundingBox/followCaster/repel) that predates the shape enum - equivalent to shape() == WALL. */
    public boolean flat() {
        return shape() == BarrierShape.WALL;
    }

    /** "bur" - inverted exclusion: traps whatever is inside instead of keeping others out. See enforceExclusion(). */
    public boolean cage() {
        return this.entityData.get(DATA_CAGE);
    }

    public float facingYaw() {
        return this.entityData.get(DATA_FACING_YAW);
    }

    public UUID casterId() {
        return casterId;
    }

    public boolean isGround() {
        return ground;
    }

    /** Placed barriers created by varnbinda are stationary without being permanent/saved ground wards. */
    public boolean isStationary() {
        return stationary;
    }

    public float strength() {
        return strength;
    }

    public float maxStrength() {
        return maxStrength;
    }

    public int color() {
        return this.entityData.get(DATA_COLOR);
    }

    public int fadeColor() {
        return this.entityData.get(DATA_FADE_COLOR);
    }

    public MagicAffinity affinity() {
        return affinity;
    }

    public boolean reflective() {
        return reflective;
    }

    public void setReflective(boolean reflective) {
        this.reflective = reflective;
    }

    /**
     * Server-side virtual spell impact used by composed elemental workings.  Returns true when
     * the barrier survives and therefore fully blocks the working; a barrier that shatters lets
     * a weakened remainder continue through.
     */
    public boolean absorbSpellImpact(float spellPower, java.util.List<com.dragonspeech.engine.Element> incoming, Vec3 hitPos) {
        if (!(level() instanceof ServerLevel level) || isRemoved()) return false;
        float pressure = Math.max(.5f, spellPower * 4.0f) * affinity.pressureMultiplier(incoming);
        boolean collapsed = false;
        if (sustainMode == SustainMode.RESERVE) {
            strength -= pressure;
            collapsed = strength <= 0f;
        } else if (sustainMode == SustainMode.CASTER) {
            collapsed = !drainCasterStamina(level, pressure);
        }
        // DURATION barriers have no durability pool: spell impacts are blocked until the timer ends.
        SpellFx.burst(level, DragonSpeechParticles.SPARKLE, color(), fadeColor(), hitPos, 12, 0.14);
        SpellFx.flash(level, color(), hitPos);
        if (collapsed) {
            SpellFx.burst(level, DragonSpeechParticles.SPARKLE, color(), fadeColor(), hitPos, 24, 0.25);
            discard();
            return false;
        }
        return true;
    }

    /** True if `entity`'s position falls inside this barrier's protected volume - used by BarrierProtection for non-collision damage (melee, explosions, fire). */
    public boolean protects(LivingEntity entity) {
        return computeBoundingBox().inflate(0.3).contains(entity.position());
    }

    /** The nearest active ground shield within `searchRadius` of `point`, if any - used by BarrierEffectHandler to decide "feed this one" vs "plant a new one". */
    public static Optional<MagicBarrierEntity> findGroundShieldNear(ServerLevel level, Vec3 point, double searchRadius) {
        List<MagicBarrierEntity> found = level.getEntitiesOfClass(MagicBarrierEntity.class,
                new AABB(point, point).inflate(searchRadius),
                b -> b.isGround() && !b.isRemoved());
        return found.stream().min((a, b) -> Double.compare(a.position().distanceToSqr(point), b.position().distanceToSqr(point)));
    }

    /** "letta skjoldr" (no ristmark): find the caster's OWN active personal shield to dispel - unlike ground shields, personal ones aren't communal, so this only ever matches something YOU cast. A personal shield always tracks the caster's live position (see followCaster()), so searching close around them is always sufficient regardless of how large it's grown. */
    public static Optional<MagicBarrierEntity> findPersonalShieldFor(ServerLevel level, UUID casterId) {
        Vec3 point = level.getServer() != null && level.getServer().getPlayerList().getPlayer(casterId) != null
            ? level.getServer().getPlayerList().getPlayer(casterId).position()
            : null;
        if (point == null) {
            return Optional.empty();
        }
        List<MagicBarrierEntity> found = level.getEntitiesOfClass(MagicBarrierEntity.class,
                new AABB(point, point).inflate(PERSONAL_MAX_RADIUS * 2),
                b -> !b.isGround() && !b.isStationary() && !b.isRemoved() && casterId.equals(b.casterId()));
        return found.stream().findFirst();
    }

    /** Nearest temporary placed barrier belonging to this caster near the selected point. */
    public static Optional<MagicBarrierEntity> findOwnedBoundaryNear(ServerLevel level, UUID casterId, Vec3 point, double searchRadius) {
        List<MagicBarrierEntity> found = level.getEntitiesOfClass(MagicBarrierEntity.class,
                new AABB(point, point).inflate(searchRadius),
                b -> !b.isGround() && b.isStationary() && !b.isRemoved() && casterId.equals(b.casterId()));
        return found.stream().min((a, b) -> Double.compare(a.position().distanceToSqr(point), b.position().distanceToSqr(point)));
    }

    /** Nearest active barrier of any lifecycle owned by this caster. */
    public static Optional<MagicBarrierEntity> findOwnedBarrierNear(ServerLevel level, UUID casterId, Vec3 point, double searchRadius) {
        List<MagicBarrierEntity> found = level.getEntitiesOfClass(MagicBarrierEntity.class,
                new AABB(point, point).inflate(searchRadius),
                b -> !b.isRemoved() && casterId.equals(b.casterId()));
        return found.stream().min((a, b) -> Double.compare(a.position().distanceToSqr(point), b.position().distanceToSqr(point)));
    }

    /**
     * Finds the first barrier actually crossed by the player's gaze. Barrier entities are intentionally
     * non-solid to vanilla movement, so ordinary entity ray helpers are not dependable for this job;
     * intersecting the barrier's own live bounding box keeps sensing aligned with the real shield body.
     */
    public static Optional<MagicBarrierEntity> findBarrierInSight(ServerLevel level, ServerPlayer player, double reach, boolean ownedOnly) {
        Vec3 start = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        if (look.lengthSqr() < 1.0e-8) return Optional.empty();
        Vec3 end = start.add(look.normalize().scale(reach));
        AABB sweep = new AABB(start, end).inflate(1.0);

        MagicBarrierEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (MagicBarrierEntity barrier : level.getEntitiesOfClass(MagicBarrierEntity.class, sweep,
                b -> !b.isRemoved() && (!ownedOnly || player.getUUID().equals(b.casterId())))) {
            Optional<Vec3> hit = barrier.getBoundingBox().inflate(0.12).clip(start, end);
            if (hit.isEmpty()) continue;
            double distance = start.distanceToSqr(hit.get());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = barrier;
            }
        }
        return Optional.ofNullable(best);
    }

    // ============================== Bounding box ==============================

    private AABB computeBoundingBox() {
        Vec3 center = position();
        double r = radius();

        if (!flat()) {
            return new AABB(center.x - r, center.y - r, center.z - r, center.x + r, center.y + r, center.z + r);
        }

        double yawRad = Math.toRadians(facingYaw());
        double dirX = -Math.sin(yawRad);
        double dirZ = Math.cos(yawRad);
        double sideX = -dirZ;
        double sideZ = dirX;

        double halfThickness = WALL_THICKNESS / 2.0;
        double xExtent = Math.abs(sideX) * r + Math.abs(dirX) * halfThickness + 0.05;
        double zExtent = Math.abs(sideZ) * r + Math.abs(dirZ) * halfThickness + 0.05;

        return new AABB(center.x - xExtent, center.y - r, center.z - zExtent,
                center.x + xExtent, center.y + r, center.z + zExtent);
    }

    /**
     * canBeCollidedWith() is deliberately always false now - it's an
     * all-or-nothing flag, and what's actually wanted ("the caster and
     * players move freely, everything else is kept out") can't be
     * expressed with it at all: it has no way to ask WHO is colliding.
     * Real movement-blocking now happens in enforceExclusion() below,
     * called every tick, which CAN discriminate by entity.
     */
    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    /**
     * "Like EBW's bubble shield" - a real barrier, not a passthrough
     * zone: PLAYERS always move freely, in and out, through any shield
     * (their own or anyone else's - you're never trapped by a shield,
     * personal or communal). Every OTHER living entity - every hostile
     * and passive mob alike - is repelled the instant it tries to cross
     * INTO the volume from outside, in WARD mode (the default).
     *
     * "bur" flips this into CAGE mode - the exact inverse: non-player
     * entities are repelled the instant they try to cross OUT of the
     * volume instead, trapping whatever is inside (however it got
     * there - there's no "captured at cast time" list, just "is it
     * inside right now"). PLAYERS ARE STILL EXEMPT in cage mode too,
     * for the same reason wards exempt them - this traps mobs, not
     * players; a player-capable cage (for PvP-style traps) would need
     * an explicit, separate decision to drop that exemption.
     *
     * This is a genuinely simpler rule than "block only hostile mobs" -
     * it doesn't need any friend/foe classification (which would also
     * have to answer questions like "is a tamed wolf hostile," "is a
     * zombie villager hostile," "is a raider currently targeting a
     * resident" - all real, all needing their own design pass). The
     * real limitation: a player's own tamed animals are blocked out
     * (ward) or trapped in (cage) exactly like anything else non-player.
     * If you want those to pass too, that's the natural next refinement,
     * but it needs an explicit ownership/reputation check this method
     * doesn't attempt.
     *
     * Implemented as an active per-tick position check + push-back
     * rather than vanilla's own collision system, specifically because
     * that system can't discriminate by entity in the way this needs -
     * see canBeCollidedWith() above.
     */
    /** How many points along an entity's last-tick-to-this-tick path get checked, not just the two endpoints - see enforceExclusion()'s doc for why endpoint-only checking lets fast-moving entities tunnel through undetected. */
    private static final int CROSSING_SAMPLE_STEPS = 6;

    /**
     * WARD: catch it trying to get IN. CAGE: catch it trying to get OUT.
     *
     * BUG FIX - "entities occasionally just walk/phase through without
     * being pushed": the original check only tested the two ENDPOINTS of
     * an entity's movement this tick (its position last tick vs. now).
     * A sufficiently fast entity (knockback, a sprint, a fall, anything
     * covering more than the shield's own thickness in one tick) can
     * start outside, cross all the way through, and land back outside
     * again - or start inside a cage and land clear outside it - all
     * within that single tick, so BOTH sampled endpoints read "outside"
     * (or both "inside") even though the entity genuinely passed through
     * the boundary. This is classic tunneling: the check was correct for
     * where the entity ended up, it just never looked at the path
     * between. Now interpolates several points along that path and
     * treats a crossing detected at ANY of them the same as an endpoint
     * crossing - cheap (a handful of AABB.contains() calls, not real
     * swept collision), but closes the gap for anything but the most
     * extreme velocities.
     */
    private void enforceExclusion(ServerLevel level) {
        AABB box = computeBoundingBox();
        AABB scanBox = box.inflate(1.5);
        boolean cage = cage();

        for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, scanBox, MagicBarrierEntity::isSubjectToExclusion)) {

            Vec3 now = candidate.position();
            Vec3 previous = new Vec3(candidate.xo, candidate.yo, candidate.zo);

            if (crossedTheWrongWay(box, previous, now, cage)) {
                repel(level, candidate, cage);
            }
        }
    }

    /**
     * "marka blidr"/"marka illr" - see MarkRegistry. A GOOD mark passes
     * through any ward/cage exactly like a player always does; a BAD
     * mark is always subject to one, even overriding the normal player
     * exemption. Unmarked entities fall back to the original rule:
     * everything except players.
     */
    private static boolean isSubjectToExclusion(LivingEntity entity) {
        if (!entity.isAlive()) {
            return false;
        }
        java.util.Optional<com.dragonspeech.engine.EntityMark> mark = com.dragonspeech.engine.MarkRegistry.get(entity);
        if (mark.isPresent()) {
            return mark.get() == com.dragonspeech.engine.EntityMark.BAD;
        }
        return !(entity instanceof ServerPlayer);
    }

    private static boolean crossedTheWrongWay(AABB box, Vec3 previous, Vec3 now, boolean cage) {
        boolean wasInsideBefore = box.contains(previous);
        boolean isInsideNow = box.contains(now);

        // Fast path: the ordinary, non-tunnelling case already used to
        // be the whole check.
        if (cage ? (!isInsideNow && wasInsideBefore) : (isInsideNow && !wasInsideBefore)) {
            return true;
        }
        // Both endpoints read the same (both outside for a ward, both
        // inside for a cage) - normally means "no crossing happened,"
        // but could also mean "crossed through and came out the other
        // side within this one tick." Only worth the extra sampling
        // when that's actually possible - i.e. the endpoints agree.
        if (wasInsideBefore == isInsideNow) {
            for (int i = 1; i < CROSSING_SAMPLE_STEPS; i++) {
                double t = (double) i / CROSSING_SAMPLE_STEPS;
                Vec3 sample = previous.lerp(now, t);
                boolean sampleInside = box.contains(sample);
                if (cage ? (wasInsideBefore && !sampleInside) : (!wasInsideBefore && sampleInside)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** How far inside the boundary a cage nudges an escaping entity back to - a short local correction, NOT scaled by the shield's radius (see the bug this replaced, in the class-adjacent commit history: computing the cage push the same way as a ward's outward push sent entities flying clear across to the opposite wall instead of just being turned back). */
    private static final double CAGE_INWARD_NUDGE = 0.8;

    private void repel(ServerLevel level, LivingEntity entity, boolean inward) {
        Vec3 center = position();
        Vec3 away = entity.position().subtract(center);
        BarrierShape shape = shape();

        Vec3 pushDir;
        if (shape == BarrierShape.WALL) {
            // A wall only pushes perpendicular to its own face - it
            // should never lift or drop the entity, only turn them back
            // (or, in cage mode, keep them from crossing through it the
            // other way - a flat cage wall is unusual but not refused).
            double yawRad = Math.toRadians(facingYaw());
            Vec3 normal = new Vec3(-Math.sin(yawRad), 0, Math.cos(yawRad));
            double side = away.dot(normal);
            Vec3 outward = side >= 0 ? normal : normal.scale(-1);
            pushDir = inward ? outward.scale(-1) : outward;
        } else if (shape == BarrierShape.CUBE) {
            // Snap to whichever axis the entity is furthest along - a
            // simple, cheap approximation of "push out/in through the
            // nearest face" without needing real AABB penetration math.
            Vec3 outward = dominantAxisDirection(away);
            pushDir = inward ? outward.scale(-1) : outward;
        } else {
            // A sphere pushes along the full 3D direction the entity
            // approached from (or, in cage mode, back the other way) -
            // if they came from below or above, the push carries that
            // same vertical component naturally.
            Vec3 outward = away.lengthSqr() > 0.0001 ? away.normalize() : new Vec3(0, 1, 0);
            pushDir = inward ? outward.scale(-1) : outward;
        }

        Vec3 safe;
        if (inward) {
            // CAGE: a short nudge back from wherever the entity actually
            // is right now, near the wall it just tried to cross - NOT
            // an offset from the shield's CENTER (that formula is
            // correct for pushing OUT to just past the boundary, but
            // applied inward it puts them at the same extreme distance
            // from centre on the OPPOSITE side - i.e. clear across the
            // whole shield, which is the exact "teleported to the
            // opposite side" bug this fixes).
            Vec3 nudged = entity.position().add(pushDir.scale(CAGE_INWARD_NUDGE));
            safe = (shape == BarrierShape.WALL) ? new Vec3(nudged.x, entity.getY(), nudged.z) : nudged;
        } else if (shape == BarrierShape.WALL) {
            Vec3 pushed = center.add(pushDir.scale(WALL_THICKNESS / 2.0 + 0.6));
            safe = new Vec3(pushed.x, entity.getY(), pushed.z);
        } else {
            // WARD (sphere/cube): push to just outside the boundary,
            // measured from the shield's centre - correct here, since
            // "just past the edge, wherever on the edge you currently
            // are" and "centre + radius in this direction" are the same
            // point for an outward push.
            safe = center.add(pushDir.scale(radius() + 0.6));
        }

        entity.teleportTo(safe.x, safe.y, safe.z);
        entity.setDeltaMovement(pushDir.scale(0.35));
        entity.hurtMarked = true;

        SpellFx.burst(level, DragonSpeechParticles.SPARKLE, color(), fadeColor(),
                entity.position().add(0, entity.getBbHeight() * 0.5, 0), 6, 0.1);
    }

    /** The unit axis vector (±X, ±Y, or ±Z) closest to `v`'s own direction - used by CUBE's push-out/push-in so a boxy shape shoves along a face normal instead of diagonally. */
    private static Vec3 dominantAxisDirection(Vec3 v) {
        double ax = Math.abs(v.x), ay = Math.abs(v.y), az = Math.abs(v.z);
        if (ax >= ay && ax >= az) {
            return new Vec3(Math.signum(v.x == 0 ? 1 : v.x), 0, 0);
        } else if (ay >= ax && ay >= az) {
            return new Vec3(0, Math.signum(v.y == 0 ? 1 : v.y), 0);
        } else {
            return new Vec3(0, 0, Math.signum(v.z == 0 ? 1 : v.z));
        }
    }

    // ============================== Damage ==============================

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide() || isRemoved()) {
            return false;
        }
        absorbDamage(amount, source);
        return true;
    }

    /** Shared entry point for both a direct hurt() hit and BarrierProtection's redirected melee/explosion/fire damage. */
    public void absorbDamage(float amount, DamageSource source) {
        if (!(level() instanceof ServerLevel level) || isRemoved()) {
            return;
        }

        boolean collapsed = false;
        if (sustainMode == SustainMode.RESERVE) {
            strength -= amount;
            collapsed = strength <= 0f;
        } else if (sustainMode == SustainMode.CASTER) {
            collapsed = !drainCasterStamina(level, amount);
        }
        // DURATION barriers do not possess a durability pool. Hits are blocked until their timer ends.

        Vec3 fxPos = position();
        SpellFx.burst(level, DragonSpeechParticles.SPARKLE, color(), fadeColor(), fxPos, 8, 0.1);

        if (source.getEntity() instanceof LivingEntity attacker && affinity != MagicAffinity.ARCANE && level.getServer() != null) {
            ServerPlayer caster = level.getServer().getPlayerList().getPlayer(casterId);
            if (caster != null) {
                affinity.retaliate(caster, attacker, 1.5f);
            }
        }

        if (collapsed) {
            SpellFx.flash(level, color(), fxPos);
            SpellFx.burst(level, DragonSpeechParticles.SPARKLE, color(), fadeColor(), fxPos, 20, 0.2);
            if (!ground && level.getServer() != null) {
                ServerPlayer caster = level.getServer().getPlayerList().getPlayer(casterId);
                if (caster != null) {
                    caster.sendSystemMessage(Component.literal("Your shield shatters."));
                }
            }
            discard();
        }
    }

    // ============================== "aflbinda" - stamina-linked strength ==============================

    /** How much of the caster's stamina one point of absorbed damage costs. Deliberately less than 1:1 - stamina pools start at 20 (StaminaMilestones grows it slowly), while shield strength routinely runs into the tens or hundreds, so a straight 1:1 trade would let a couple of hits drain a new caster completely. Tune this constant, not the call sites, if the balance feels off. */
    private static final float STAMINA_PER_DAMAGE = 0.4f;

    /**
     * Spends the caster's stamina to cover as much of `amount` as their
     * pool allows, and returns whatever's LEFT for strength to absorb
     * normally. This is a hybrid on purpose: stamina takes the hit
     * first, but a shield linked to an exhausted caster doesn't become
     * either invincible (bad) or instantly worthless (also not what
     * "aflbinda" implies) - once stamina runs dry, the shield's own
     * remaining strength picks up the overflow, same as an un-bound one.
     */

    /**
     * FIXED - this used to touch ONLY stamina, full stop: once stamina
     * hit 0, every further hit's cost was declared "uncovered" and
     * handed to the shield's own strength... which sounds right, but
     * the actual bug was upstream of that framing entirely. What you
     * were seeing ("stuck on half a heart, but the spell still stays")
     * wasn't from THIS method at all - it was your own ordinary spell
     * casting (via DrainResolver, which already correctly cascades
     * stamina -> hunger -> health, capped at
     * DragonSpeechConfig.minSurvivableHealth() specifically so an
     * unaffordable cast fails instead of killing you) running you down
     * to that same floor. This method never knew that floor existed, so
     * it never touched hunger or health at all, and a shield linked to
     * an already-tapped-out caster just kept absorbing hits for free -
     * stamina was empty, "uncovered" was correctly computed, but nothing
     * meaningful ever happened with that number beyond this method.
     *
     * NOW FURTHER REFACTORED: the actual stamina->hunger->health cascade
     * moved out to CasterStaminaCascade, a standalone shared utility -
     * item-enchanted stamina-linked WARDS (see MagicWardCombat) need the
     * exact same three-tier, floor-respecting behavior this method
     * pioneered, and duplicating it a second time risked the two
     * silently drifting apart the next time either one needed a tweak.
     * This method is now just the "is aflbinda actually able to charge
     * someone" wrapper (finding the caster, applying this system's own
     * damage-to-energy conversion rate) around that shared cascade.
     */
    private boolean drainCasterStamina(ServerLevel level, float amount) {
        if (casterId == null || level.getServer() == null) return false;
        ServerPlayer caster = level.getServer().getPlayerList().getPlayer(casterId);
        if (caster == null) return true; // don't destroy a binding only because its caster is offline
        var data = com.dragonspeech.stamina.StaminaAccess.get(caster);
        float requested = Math.max(0f, amount * STAMINA_PER_DAMAGE);
        if (data.stamina() <= 0f || data.stamina() < requested) {
            com.dragonspeech.stamina.StaminaAccess.set(caster, data.withStamina(0f));
            return false;
        }
        com.dragonspeech.stamina.StaminaAccess.set(caster, data.withStamina(data.stamina() - requested));
        return true;
    }

    // ============================== Tick / lifetime ==============================

    /** How far ahead of the caster's current facing a personal wall shield stays - matches BarrierEffectHandler's initial placement distance, kept here too since this is what re-anchors it every tick afterward. */
    private static final double WALL_FOLLOW_DISTANCE = 2.0;

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            return;
        }

        if (level() instanceof ServerLevel level) {
            followCaster(level);
        }
        if (!isRemoved()) {
            this.setBoundingBox(computeBoundingBox());
        }
        // spawnShellParticleIfNeeded() used to run here - the visible
        // shell is now a real drawn sphere (MagicBarrierRenderer), not a
        // particle, so spawning ShieldShellParticle on top of it would
        // just double up the visual. The method/field are left in place,
        // unused, rather than deleted, in case you want to fall back to
        // the particle approach for some reason.

        if (level() instanceof ServerLevel level) {
            enforceExclusion(level);
        }

        // Default constructs are duration-based, including placed/ground barriers. `afla` and
        // `aflbinda` explicitly replace that timer with reserve/caster-stamina sustain.
        if (sustainMode == SustainMode.DURATION && tickCount >= lifetimeTicks) {
            if (level() instanceof ServerLevel level) SpellFx.flash(level, color(), position());
            discard();
            return;
        }
        if (sustainMode == SustainMode.CASTER && level() instanceof ServerLevel level && casterId != null && level.getServer() != null) {
            ServerPlayer caster = level.getServer().getPlayerList().getPlayer(casterId);
            if (caster != null && com.dragonspeech.stamina.StaminaAccess.get(caster).stamina() <= 0f) {
                SpellFx.flash(level, color(), position());
                discard();
                return;
            }
        }

        if (tickCount % FX_INTERVAL_TICKS == 0 && level() instanceof ServerLevel level) {
            idleAccentFx(level);
        }

        // Fire struggles to spread inside a slowed/stilled field is
        // TemporalFieldManager's job, not this class's - a barrier isn't
        // a time effect. Nothing fire-related happens here.
    }

    /**
     * BUG FIX #1 (original): a personal shield was documented as
     * following the caster but nothing anywhere ever actually moved it
     * after the initial setPos() at cast time - it just sat wherever it
     * was cast forever. Ground shields (ground=true) are deliberately
     * excluded - those are meant to stay planted exactly where they
     * were placed.
     *
     * BUG FIX #2: personal shields were centred on the CASTER'S FEET
     * (Entity position is always feet-level in Minecraft), so roughly
     * half the sphere/cube's vertical extent was wasted underground and
     * the top sat barely above eye height - "the shield doesn't cover
     * me, I can see over the top of it" was a direct symptom of this.
     * Now centred on the caster's actual body midpoint instead (feet +
     * half their bounding-box height) for both the sphere/cube case and
     * the wall case - a wall shield had the exact same feet-anchored
     * problem, just less obviously since a wall only needs vertical
     * coverage, not "does the top clear my head" the way a dome does.
     *
     * BUG FIX #3: a personal CAGE now follows whoever it actually
     * trapped (targetId - see BarrierEffectHandler.resolveCageTarget()),
     * not the caster. A cage that chased the CASTER around while
     * "trapping" someone else made no sense - the whole point is that it
     * stays centred on the thing it caught. If the target despawns/dies/
     * logs off, the cage just holds its last position and runs out its
     * remaining lifetime rather than collapsing outright.
     */
    private void followCaster(ServerLevel level) {
        if (ground || stationary || level.getServer() == null) {
            return;
        }

        if (cage() && targetId != null) {
            Entity target = level.getEntity(targetId);
            if (target != null && target.isAlive()) {
                this.setPos(target.getX(), target.getY(), target.getZ());
            }
            // Target gone - hold last known position, same as a caster
            // logging off below; the cage still expires normally.
            return;
        }

        if (casterId == null) {
            return;
        }
        ServerPlayer caster = level.getServer().getPlayerList().getPlayer(casterId);
        if (caster == null || !caster.isAlive()) {
            return; // caster logged off or died - the shield just holds its last position until it expires
        }

        double bodyCenterY = caster.getY() + caster.getBbHeight() * 0.5;

        if (!flat()) {
            this.setPos(caster.getX(), bodyCenterY, caster.getZ());
        } else {
            Vec3 ahead = caster.getLookAngle().multiply(1, 0, 1);
            ahead = ahead.lengthSqr() > 0.0001 ? ahead.normalize() : new Vec3(0, 0, 1);
            Vec3 wallPos = caster.position().add(ahead.scale(WALL_FOLLOW_DISTANCE));
            this.setPos(wallPos.x, bodyCenterY, wallPos.z);
            this.entityData.set(DATA_FACING_YAW, caster.getYRot());
        }
    }

    /**
     * The actual visibility fix: ONE persistent, entity-linked shell
     * particle that reads this barrier's live radius/shape every frame -
     * see ShieldShellParticle. Spawned once and left alone; it follows
     * this entity automatically and needs no per-tick respawning even as
     * a ground shield grows.
     *
     * Waits a couple ticks after this entity starts ticking (i.e. after
     * addFreshEntity() added it to the level) before actually sending -
     * vanilla's own entity-tracking broadcast to nearby clients isn't
     * guaranteed to be fully flushed in the SAME tick addFreshEntity()
     * runs in, and since setEntity() resolves the link exactly once with
     * no retry, sending too early risks the exact same permanent-null
     * link this whole method exists to avoid. Two ticks is imperceptible
     * and gives that broadcast a comfortable head start.
     */
    private void spawnShellParticleIfNeeded() {
        if (shellSpawned || tickCount < 2 || !(level() instanceof ServerLevel level)) {
            return;
        }
        shellSpawned = true;
        SpellFx.of(DragonSpeechParticles.SHIELD_SHELL)
                .pos(position())
                .entity(this)
                .color(color())
                .fade(fadeColor())
                .scale((float) (radius() / 10.0)) // matches ShieldShellParticle's quadSize*10 fallback math, in case entity linking is ever delayed further than expected
                .time(sustainMode == SustainMode.DURATION ? lifetimeTicks + 20 : Integer.MAX_VALUE / 2)
                .spawn(level);
    }

    /** Light accent sparkles on top of the solid shell - flavor, not the primary visual anymore. */
    /** Boundary-marking sparkles on top of the solid shell - now dense enough to clearly read as "the edge of something" on their own, not just flavor. */
    private void idleAccentFx(ServerLevel level) {
        double r = Math.min(radius(), 12.0); // cap the density scaling so a huge ground dome doesn't spawn an absurd count
        int points = Math.max(24, (int) (r * 6));
        int pointLifetime = FX_INTERVAL_TICKS * 3; // outlasts the refresh interval so multiple generations overlap at once, instead of one thin wave

        for (int i = 0; i < points; i++) {
            double u = level.random.nextDouble();
            double v = level.random.nextDouble();
            double theta = 2 * Math.PI * u;
            double phi = Math.acos(2 * v - 1);
            Vec3 point = position().add(
                    radius() * Math.sin(phi) * Math.cos(theta),
                    radius() * Math.cos(phi),
                    radius() * Math.sin(phi) * Math.sin(theta));
            SpellFx.of(DragonSpeechParticles.SPARKLE)
                    .pos(point).color(color()).fade(fadeColor())
                    .time(pointLifetime).scale(0.6f)
                    .spawn(level);
        }
    }

    // ============================== Persistence ==============================

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.ground = tag.getBoolean("ground");
        this.stationary = this.ground;
        if (!ground) {
            return; // personal barriers are never saved in the first place - nothing to read
        }
        this.entityData.set(DATA_RADIUS, tag.getFloat("radius"));
        if (tag.contains("shape")) {
            try {
                this.entityData.set(DATA_SHAPE, BarrierShape.valueOf(tag.getString("shape").toUpperCase(Locale.ROOT)).ordinal());
            } catch (IllegalArgumentException ignored) {
                this.entityData.set(DATA_SHAPE, BarrierShape.SPHERE.ordinal());
            }
        } else {
            // Reading a save from before the shape enum existed - it only ever had a flat/not-flat flag.
            this.entityData.set(DATA_SHAPE, (tag.getBoolean("flat") ? BarrierShape.WALL : BarrierShape.SPHERE).ordinal());
        }
        this.entityData.set(DATA_CAGE, tag.getBoolean("cage"));
        this.aflbound = tag.getBoolean("aflbound");
        try {
            this.sustainMode = tag.contains("sustainMode")
                ? SustainMode.valueOf(tag.getString("sustainMode").toUpperCase(java.util.Locale.ROOT))
                : (this.aflbound ? SustainMode.CASTER : SustainMode.RESERVE);
        } catch (IllegalArgumentException ignored) {
            this.sustainMode = this.aflbound ? SustainMode.CASTER : SustainMode.RESERVE;
        }
        this.reflective = tag.getBoolean("reflective");
        this.entityData.set(DATA_FACING_YAW, tag.getFloat("facing_yaw"));
        this.colorOverride = tag.getInt("color");
        this.fadeOverride = tag.getInt("fade_color");
        this.strength = tag.getFloat("strength");
        this.maxStrength = tag.getFloat("max_strength");
        this.lifetimeTicks = sustainMode == SustainMode.DURATION
                ? Math.max(1, tag.getInt("remaining_lifetime"))
                : Integer.MAX_VALUE / 2;
        if (tag.contains("caster_id")) {
            this.casterId = tag.getUUID("caster_id");
        }
        if (tag.contains("affinity")) {
            try {
                this.affinity = MagicAffinity.valueOf(tag.getString("affinity").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                this.affinity = MagicAffinity.ARCANE;
            }
        } else if (tag.contains("element")) {
            // Backward compatibility with pre-1.7.3 saved barriers.
            try {
                this.affinity = MagicAffinity.valueOf(tag.getString("element").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                this.affinity = MagicAffinity.ARCANE;
            }
        }
        // Same reason configure() sets these - a reloaded-from-disk
        // ground shield needs its CLIENT-visible color refreshed too,
        // now that element/colorOverride/fadeOverride are known.
        this.entityData.set(DATA_COLOR, affinity == MagicAffinity.ARCANE ? colorOverride : affinity.color());
        this.entityData.set(DATA_FADE_COLOR, affinity == MagicAffinity.ARCANE ? fadeOverride : affinity.fadeColor());
        this.setBoundingBox(computeBoundingBox());
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putBoolean("ground", ground);
        if (!ground) {
            return; // personal barriers dissipate on restart on purpose - same as every other held working in this mod
        }
        tag.putFloat("radius", (float) radius());
        tag.putString("shape", shape().name());
        tag.putBoolean("flat", flat()); // legacy field, kept for backward-read safety - "shape" is authoritative now
        tag.putBoolean("cage", cage());
        tag.putBoolean("aflbound", aflbound);
        tag.putString("sustainMode", sustainMode.name().toLowerCase(java.util.Locale.ROOT));
        tag.putBoolean("reflective", reflective);
        tag.putFloat("facing_yaw", facingYaw());
        tag.putInt("color", colorOverride);
        tag.putInt("fade_color", fadeOverride);
        tag.putFloat("strength", strength);
        tag.putFloat("max_strength", maxStrength);
        if (sustainMode == SustainMode.DURATION) {
            tag.putInt("remaining_lifetime", Math.max(1, lifetimeTicks - tickCount));
        }
        if (casterId != null) {
            tag.putUUID("caster_id", casterId);
        }
        tag.putString("affinity", affinity.name());
    }

    @Override
    public boolean shouldBeSaved() {
        return ground;
    }
}