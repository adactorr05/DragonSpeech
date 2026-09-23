package com.dragonspeech.effect;

import com.dragonspeech.DragonSpeech;
import com.dragonspeech.engine.MagicAffinity;
import com.dragonspeech.engine.Strikes;
import com.dragonspeech.entity.BarrierShape;
import com.dragonspeech.entity.DragonSpeechEntities;
import com.dragonspeech.entity.MagicBarrierEntity;
import com.dragonspeech.spell.RepetitionCost;
import com.dragonspeech.spell.SustainMode;
import com.dragonspeech.word.Word;
import com.dragonspeech.word.WordCategory;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Backs "skjoldr" - see the class comment on MagicBarrierEntity for the
 * physical/visual side. This handler has two distinct modes, chosen by
 * whether "ristmark" is spoken alongside it - the same "words compose,
 * they don't multiply the dictionary" approach used everywhere else in
 * this grammar, rather than a second dedicated verb:
 *
 * PERSONAL (no ristmark): unchanged from before - a bubble or wall that
 * follows the caster and expires after a power-scaled duration.
 *
 * GROUND ("skjoldr ristmark"): plants a fixed-position shield at the
 * ground you're looking at. Doesn't expire from age, survives a server
 * restart, and re-casting the same words near it FEEDS it instead of
 * planting a second one on top:
 *
 *   skjoldr ristmark              - plant a new ground shield here
 *   skjoldr ristmark afla         - pour stored energy into the nearest
 *                                    one within reach (heals/strengthens it)
 *   skjoldr ristmark afla afla    - the same, repeated - more energy,
 *                                    at RepetitionCost's escalating price
 *   skjoldr ristmark afla mikla   - feed it AND grow its radius
 *   skjoldr ristmark litla        - shrink it (free - you're removing
 *                                    material, not adding it)
 *
 * ANYONE can feed or resize an existing ground shield, not just whoever
 * planted it - a shield around a village is meant to be a shared
 * project, not locked to one player forever.
 *
 * SHAPE ("teningr"/"kringlott"/"flata", any mode): SPHERE is the default
 * and needs no word; "teningr" (cube) or "flata" (wall) switch it - see
 * BarrierShape. "kringlott" explicitly names the sphere default, purely
 * so a sentence can say it outright the same way "seida" can for
 * vopnbinda - it doesn't change anything, it's just precision you get
 * to spend the cost-multiplier on if you want to.
 *
 * "bur" (any mode): CAGE instead of WARD - the working's exclusion
 * flips, trapping whatever is inside rather than keeping others out.
 * See MagicBarrierEntity.enforceExclusion() for exactly how the
 * inversion works and its one deliberate limitation (players are still
 * exempt, same as a ward - this traps mobs, not people).
 */
public class BarrierEffectHandler implements EffectHandler {

    private static final double PERSONAL_DEFAULT_RADIUS = 2.0;
    private static final double GROUND_DEFAULT_RADIUS = 4.0;
    private static final double WALL_DISTANCE_AHEAD = 2.0;
    private static final double GROUND_PLACEMENT_RANGE = 20.0;
    private static final double GROUND_DROP_MAX = 12.0;
    /** How far a personal "bur" (cage) will reach to find something to trap - same idea as GROUND_PLACEMENT_RANGE but for a live entity target instead of a point on the ground. */
    private static final double CAGE_TARGET_RANGE = 16.0;

    private static final double GROW_RADIUS_DELTA = 2.0;
    private static final float GROW_COST = 15f;
    private static final float FEED_BASE_UNIT_FRACTION = 0.15f; // 15% of the shield's current max per afla utterance
    private static final float FEED_BASE_UNIT_MIN = 10f;

    private static final EffectHandlerCaps CAPS = new EffectHandlerCaps(
        1, 30.0f, (float) GROUND_PLACEMENT_RANGE, Set.of(TargetKind.ENTITY)
    );

    @Override
    public ResourceLocation id() {
        return DragonSpeech.id("barrier");
    }

    @Override
    public EffectHandlerCaps caps() {
        return CAPS;
    }

    @Override
    public boolean selfTargeting() {
        return true;
    }

    @Override
    public float estimateBaseMagnitude(EffectInvocation invocation) {
        if (isDispel(invocation)) {
            return 2f; // releasing your own working is cheap - same spirit as litla being free
        }
        if (isBoundaryMode(invocation) && !isGroundMode(invocation)) {
            ServerPlayer caster = invocation.caster();
            if (caster.level() instanceof ServerLevel level && isFeedRequest(invocation)) {
                Vec3 target = resolveBoundaryTarget(invocation, caster, level);
                Optional<MagicBarrierEntity> existing = MagicBarrierEntity.findOwnedBoundaryNear(
                    level, caster.getUUID(), target, MagicBarrierEntity.FEED_SEARCH_RADIUS);
                if (existing.isPresent()) return 2f + feedCost(invocation, existing.get());
            }
            double radius = resolveRadius(invocation, false);
            BarrierShape shape = resolveShape(invocation);
            float cageFactor = isCage(invocation) ? 1.3f : 1f;
            // A placed boundary is a little harder than carrying a personal shield because the
            // caster must hold a fixed location in the world rather than their own body.
            return (float) (7.0 + radius * 2.3) * shapeFactor(shape) * cageFactor;
        }
        if (!isGroundMode(invocation)) {
            double radius = resolveRadius(invocation, false);
            BarrierShape shape = resolveShape(invocation);
            float shapeFactor = shapeFactor(shape);
            float cageFactor = isCage(invocation) ? 1.3f : 1f; // holding something in fights it every tick it tries to leave - costs a bit more than simply keeping others out
            return (float) (5.0 + radius * 2.0) * shapeFactor * cageFactor;
        }

        ServerPlayer caster = invocation.caster();
        if (!(caster.level() instanceof ServerLevel level)) {
            return 8f;
        }
        Vec3 target = resolveGroundTarget(caster, level);
        Optional<MagicBarrierEntity> existing = MagicBarrierEntity.findGroundShieldNear(level, target, MagicBarrierEntity.FEED_SEARCH_RADIUS);

        if (existing.isEmpty()) {
            double radius = resolveRadius(invocation, true);
            return (float) (8.0 + radius * 2.5);
        }

        return 2f + feedCost(invocation, existing.get());
    }

    @Override
    public EffectResult apply(EffectInvocation invocation) {
        ServerPlayer caster = invocation.caster();
        if (!(caster.level() instanceof ServerLevel level)) {
            return EffectResult.failure("The working slips away.");
        }

        if (isDispel(invocation)) {
            return applyDispel(invocation, caster, level);
        }
        if (isGroundMode(invocation)) {
            return applyGround(invocation, caster, level);
        }
        if (isBoundaryMode(invocation)) {
            return applyBoundary(invocation, caster, level);
        }
        return applyPersonal(invocation, caster, level);
    }

    // ============================== "letta skjoldr" - dispel ==============================

    /**
     * "letta" (control, "cease/be still") spoken alongside "skjoldr":
     * releases a shield instead of casting a new one. Without "ristmark"
     * it releases the CASTER'S OWN active personal shield (never someone
     * else's - a personal working isn't communal the way a ground shield
     * is). With "ristmark", it releases the nearest GROUND shield within
     * reach instead - open to anyone, same "anyone can feed a village
     * shield" spirit as the rest of ground-shield handling.
     */
    private EffectResult applyDispel(EffectInvocation invocation, ServerPlayer caster, ServerLevel level) {
        if (isGroundMode(invocation)) {
            Vec3 target = resolveGroundTarget(caster, level);
            Optional<MagicBarrierEntity> existing = MagicBarrierEntity.findGroundShieldNear(level, target, MagicBarrierEntity.FEED_SEARCH_RADIUS);
            if (existing.isEmpty()) {
                return EffectResult.failure("There is no standing barrier here to release.");
            }
            existing.get().dispel();
            return EffectResult.success(1, "The word unwinds what was bound - the shield here falls still and fades.");
        }

        if (isBoundaryMode(invocation)) {
            Vec3 target = resolveBoundaryTarget(invocation, caster, level);
            Optional<MagicBarrierEntity> existing = MagicBarrierEntity.findOwnedBoundaryNear(level, caster.getUUID(), target, MagicBarrierEntity.FEED_SEARCH_RADIUS);
            if (existing.isEmpty()) {
                return EffectResult.failure("There is no boundary of yours there to release.");
            }
            existing.get().dispel();
            return EffectResult.success(1, "The bound boundary loosens and falls away.");
        }

        Optional<MagicBarrierEntity> existing = MagicBarrierEntity.findPersonalShieldFor(level, caster.getUUID());
        if (existing.isEmpty()) {
            return EffectResult.failure("You have no standing barrier to release.");
        }
        existing.get().dispel();
        return EffectResult.success(1, "The word unwinds what was bound - your shield falls still and fades.");
    }

    // ============================== Personal ==============================

    private EffectResult applyPersonal(EffectInvocation invocation, ServerPlayer caster, ServerLevel level) {
        float verbPrecision = invocation.composition().wordsOf(WordCategory.VERB).stream()
            .findFirst().map(Word::precision).orElse(0.5f);
        float power = 3f + verbPrecision * 4f + Math.max(0f, invocation.modifierMagnitudeSum()) * 3f;
        power = Math.max(1f, Math.min(power, CAPS.maxMagnitudePerTarget()));

        double radius = resolveRadius(invocation, false);
        BarrierShape shape = resolveShape(invocation);
        boolean cage = isCage(invocation);
        boolean aflbound = isAflbound(invocation);
        SustainMode sustainMode = SustainMode.from(invocation.composition());
        boolean flat = shape == BarrierShape.WALL;
        MagicAffinity affinity = resolveAffinity(invocation);

        float strength = 12f + power * 4f;
        if (sustainMode == SustainMode.RESERVE) {
            strength *= 1f + Math.max(0, invocation.composition().occurrencesOf("afla") - 1) * 0.5f;
        }
        int lifetimeTicks = Math.round(20 * (8 + power * 2));

        // A CAGE traps whatever you're looking at - it has no reason to
        // be centred on yourself the way a protective WARD does. Falls
        // back to the caster only if nothing was actually targeted, so
        // "bur" alone never just fails outright.
        LivingEntity cageTarget = null;
        if (cage) {
            cageTarget = resolveCageTarget(caster);
        }
        Entity anchor = cageTarget != null ? cageTarget : caster;
        float facingYaw = caster.getYRot();

        double bodyCenterY = anchor.getY() + anchor.getBbHeight() * 0.5;
        Vec3 wallFacing = horizontalFacing(caster);
        double wallSign = hasWord(invocation, "aftana") && !hasWord(invocation, "frama") ? -1.0 : 1.0;
        Vec3 spawnPos = flat
            ? anchor.position().add(wallFacing.scale(WALL_DISTANCE_AHEAD * wallSign))
            : anchor.position();

        int defaultColor = cage ? MagicBarrierEntity.CAGE_DEFAULT_COLOR : MagicBarrierEntity.DEFAULT_COLOR;
        int defaultFade = cage ? MagicBarrierEntity.CAGE_DEFAULT_FADE : MagicBarrierEntity.DEFAULT_FADE;

        MagicBarrierEntity barrier = new MagicBarrierEntity(DragonSpeechEntities.MAGIC_BARRIER, level);
        barrier.setPos(spawnPos.x, bodyCenterY, spawnPos.z);
        barrier.configure(caster, radius, shape, cage, facingYaw,
            affinity,
            affinity == MagicAffinity.ARCANE ? defaultColor : affinity.color(),
            affinity == MagicAffinity.ARCANE ? defaultFade : affinity.fadeColor(),
            strength, lifetimeTicks, false, false, cageTarget, aflbound, sustainMode);
        barrier.setReflective(hasWord(invocation, "sveigja"));

        if (!level.addFreshEntity(barrier)) {
            return EffectResult.failure("There is no room here for a shield to stand.");
        }

        String shapeWord = shapeNoun(shape);
        String elementWord = affinity == MagicAffinity.ARCANE ? "" : affinity.getSerializedName() + "-bound ";
        String message;
        if (cage && cageTarget != null) {
            message = "A " + elementWord + shapeWord + " cage of light closes about what you marked, and holds it there.";
        } else if (cage) {
            message = "A " + elementWord + shapeWord + " cage of light answers, and closes about what stands within.";
        } else {
            message = "A " + elementWord + shapeWord + " of light answers, real and standing.";
        }
        return EffectResult.success(1, message);
    }

    // ============================== Placed boundary (varnbinda) ==============================

    /**
     * varnbinda is deliberately not a synonym for skjoldr. A shield belongs to a bearer and
     * follows them; a boundary is placed into the world and remains where it was raised for its
     * finite lifetime. Shape/material words still compose exactly the same way.
     */
    private EffectResult applyBoundary(EffectInvocation invocation, ServerPlayer caster, ServerLevel level) {
        Vec3 target = resolveBoundaryTarget(invocation, caster, level);
        if (isFeedRequest(invocation)) {
            Optional<MagicBarrierEntity> existing = MagicBarrierEntity.findOwnedBoundaryNear(
                level, caster.getUUID(), target, MagicBarrierEntity.FEED_SEARCH_RADIUS);
            if (existing.isPresent()) return feedExisting(invocation, existing.get());
        }

        double radius = resolveRadius(invocation, false);
        BarrierShape shape = resolveShape(invocation);
        boolean cage = isCage(invocation);
        boolean aflbound = isAflbound(invocation);
        SustainMode sustainMode = SustainMode.from(invocation.composition());
        MagicAffinity affinity = resolveAffinity(invocation);

        float verbPrecision = invocation.composition().wordsOf(WordCategory.VERB).stream()
            .findFirst().map(Word::precision).orElse(0.5f);
        float power = 3f + verbPrecision * 4f + Math.max(0f, invocation.modifierMagnitudeSum()) * 3f;
        power = Math.max(1f, Math.min(power, CAPS.maxMagnitudePerTarget()));
        float strength = 16f + power * 4.5f;
        if (sustainMode == SustainMode.RESERVE) {
            strength *= 1f + Math.max(0, invocation.composition().occurrencesOf("afla") - 1) * 0.5f;
        }
        int lifetimeTicks = Math.round(20f * (10f + power * 2.5f));

        float yaw = caster.getYRot();
        int defaultColor = cage ? MagicBarrierEntity.CAGE_DEFAULT_COLOR : MagicBarrierEntity.DEFAULT_COLOR;
        int defaultFade = cage ? MagicBarrierEntity.CAGE_DEFAULT_FADE : MagicBarrierEntity.DEFAULT_FADE;

        MagicBarrierEntity barrier = new MagicBarrierEntity(DragonSpeechEntities.MAGIC_BARRIER, level);
        barrier.setPos(target.x, target.y, target.z);
        barrier.configure(caster, radius, shape, cage, yaw,
            affinity,
            affinity == MagicAffinity.ARCANE ? defaultColor : affinity.color(),
            affinity == MagicAffinity.ARCANE ? defaultFade : affinity.fadeColor(),
            strength, lifetimeTicks, false, true, null, aflbound, sustainMode);
        barrier.setReflective(hasWord(invocation, "sveigja"));

        if (!level.addFreshEntity(barrier)) {
            return EffectResult.failure("There is no room there for the boundary to stand.");
        }

        String material = affinity == MagicAffinity.ARCANE ? "arcane" : affinity.getSerializedName();
        return EffectResult.success(1, "A " + material + " " + shapeNoun(shape) + " boundary knots itself into the place you named.");
    }

    // ============================== Ground (new) ==============================

    private EffectResult applyGround(EffectInvocation invocation, ServerPlayer caster, ServerLevel level) {
        Vec3 target = resolveGroundTarget(caster, level);
        Optional<MagicBarrierEntity> existing = MagicBarrierEntity.findGroundShieldNear(level, target, MagicBarrierEntity.FEED_SEARCH_RADIUS);

        if (existing.isPresent()) {
            return feedExisting(invocation, existing.get());
        }

        double radius = resolveRadius(invocation, true);
        BarrierShape shape = resolveShape(invocation);
        boolean cage = isCage(invocation);
        boolean aflbound = isAflbound(invocation);
        SustainMode sustainMode = SustainMode.from(invocation.composition());
        MagicAffinity affinity = resolveAffinity(invocation);

        float verbPrecision = invocation.composition().wordsOf(WordCategory.VERB).stream()
            .findFirst().map(Word::precision).orElse(0.5f);
        float power = 3f + verbPrecision * 4f + Math.max(0f, invocation.modifierMagnitudeSum()) * 3f;

        float strength = 20f + power * 5f;
        if (sustainMode == SustainMode.RESERVE) {
            strength *= 1f + Math.max(0, invocation.composition().occurrencesOf("afla") - 1) * 0.5f;
        }
        int lifetimeTicks = Math.round(20f * (14f + power * 3f));

        int defaultColor = cage ? MagicBarrierEntity.CAGE_DEFAULT_COLOR : MagicBarrierEntity.DEFAULT_COLOR;
        int defaultFade = cage ? MagicBarrierEntity.CAGE_DEFAULT_FADE : MagicBarrierEntity.DEFAULT_FADE;

        MagicBarrierEntity barrier = new MagicBarrierEntity(DragonSpeechEntities.MAGIC_BARRIER, level);
        barrier.setPos(target.x, target.y, target.z);
        barrier.configure(caster, radius, shape, cage, caster.getYRot(),
            affinity,
            affinity == MagicAffinity.ARCANE ? defaultColor : affinity.color(),
            affinity == MagicAffinity.ARCANE ? defaultFade : affinity.fadeColor(),
            strength, lifetimeTicks, true, true, null, aflbound, sustainMode);
        barrier.setReflective(hasWord(invocation, "sveigja"));

        if (!level.addFreshEntity(barrier)) {
            return EffectResult.failure("There is no room here for a shield to take root.");
        }

        String message = cage
            ? "The word sinks into the earth, and a cage of light rises here - it will hold until it falls, and grow if it is fed."
            : "The word sinks into the earth, and a shield takes root here - it will stand until it falls, and grow if it is fed.";
        return EffectResult.success(1, message);
    }

    private EffectResult feedExisting(EffectInvocation invocation, MagicBarrierEntity existing) {
        int aflaCount = invocation.composition().occurrencesOf("afla");
        boolean grow = hasWord(invocation, "mikla");
        boolean shrink = hasWord(invocation, "litla");

        if (aflaCount == 0 && !grow && !shrink) {
            return EffectResult.failure(
                "It already stands here, unmoved by an empty repetition - empower it with afla, or ask less of it with litla.");
        }

        float feedUnit = Math.max(FEED_BASE_UNIT_MIN, existing.maxStrength() * FEED_BASE_UNIT_FRACTION);
        float bonusEnergy = aflaCount * feedUnit;

        double radiusDelta = 0;
        if (grow) {
            radiusDelta += GROW_RADIUS_DELTA;
        }
        if (shrink) {
            radiusDelta -= GROW_RADIUS_DELTA;
        }

        existing.feed(bonusEnergy, radiusDelta);

        String message;
        if (bonusEnergy > 0 && radiusDelta > 0) {
            message = "Strength pours into the standing barrier, and it swells larger to hold it.";
        } else if (bonusEnergy > 0) {
            message = "Strength pours into the standing barrier, and its bindings draw tighter.";
        } else if (radiusDelta > 0) {
            message = "The standing barrier strains outward, wider than before.";
        } else {
            message = "The standing barrier draws inward, smaller and easier to hold.";
        }
        return EffectResult.success(1, message);
    }

    private static float feedCost(EffectInvocation invocation, MagicBarrierEntity existing) {
        int aflaCount = invocation.composition().occurrencesOf("afla");
        boolean grow = hasWord(invocation, "mikla");

        float feedUnit = Math.max(FEED_BASE_UNIT_MIN, existing.maxStrength() * FEED_BASE_UNIT_FRACTION);
        float aflaCost = aflaCount > 0 ? feedUnit * RepetitionCost.multiplier(aflaCount) : 0f;
        float growCost = grow ? GROW_COST : 0f;
        // shrinking (litla) is free on purpose - see class comment
        return aflaCost + growCost;
    }

    // ============================== Shared helpers ==============================

    private static boolean isGroundMode(EffectInvocation invocation) {
        return hasWord(invocation, "ristmark");
    }

    /** varnbinda - a standing boundary fixed in space rather than a shield carried by the caster. */
    private static boolean isBoundaryMode(EffectInvocation invocation) {
        return hasWord(invocation, "varnbinda");
    }

    /** afla strengthens an existing placed boundary; mikla/litla resize it instead of stacking a duplicate. */
    private static boolean isFeedRequest(EffectInvocation invocation) {
        return invocation.composition().occurrencesOf("afla") > 0
            || hasWord(invocation, "mikla") || hasWord(invocation, "litla");
    }

    private static boolean hasWord(EffectInvocation invocation, String trueName) {
        return invocation.composition().words().stream().anyMatch(w -> trueName.equals(w.trueName()));
    }

    private static Vec3 resolveGroundTarget(ServerPlayer caster, ServerLevel level) {
        Vec3 origin = caster.getEyePosition();
        Strikes.StrikeHit hit = Strikes.ray(caster, origin, caster.getLookAngle(), GROUND_PLACEMENT_RANGE);
        Vec3 aimPoint = hit.blockPos() != null ? hit.pos() : origin.add(caster.getLookAngle().scale(GROUND_PLACEMENT_RANGE * 0.5));
        return Strikes.dropToGround(level, caster, aimPoint.add(0, 1, 0), GROUND_DROP_MAX);
    }

    private static Vec3 resolveBoundaryTarget(EffectInvocation invocation, ServerPlayer caster, ServerLevel level) {
        // Explicit forward/backward means "put the boundary before/behind me", not "where my
        // crosshair eventually hits". Without a directional placement word, the looked-at mark
        // becomes the boundary center so a sphere/cube can be raised around a distant point.
        Vec3 body = caster.position().add(0, caster.getBbHeight() * .5, 0);
        Vec3 flat = horizontalFacing(caster);
        if (hasWord(invocation, "frama")) return body.add(flat.scale(WALL_DISTANCE_AHEAD));
        if (hasWord(invocation, "aftana")) return body.subtract(flat.scale(WALL_DISTANCE_AHEAD));

        if (!invocation.targets().isEmpty()) {
            EffectTarget first = invocation.targets().get(0);
            if (first instanceof EffectTarget.OfEntity entityTarget) {
                Entity e = entityTarget.entity();
                return e.position().add(0, e.getBbHeight() * .5, 0);
            }
            if (first instanceof EffectTarget.OfBlock blockTarget) return Vec3.atCenterOf(blockTarget.pos());
            if (first instanceof EffectTarget.OfDirection directionTarget) {
                Vec3 d = directionTarget.direction().normalize();
                return directionTarget.origin().add(d.scale(Math.min(8.0, GROUND_PLACEMENT_RANGE)));
            }
        }

        Strikes.StrikeHit hit = Strikes.ray(caster, caster.getEyePosition(), caster.getLookAngle(), GROUND_PLACEMENT_RANGE);
        return hit.pos();
    }

    private static double resolveRadius(EffectInvocation invocation, boolean ground) {
        float scopeRadius = invocation.composition().scopeWord().map(Word::scopeRadius).orElse(0f);
        double base = ground ? GROUND_DEFAULT_RADIUS : PERSONAL_DEFAULT_RADIUS;
        double max = ground ? MagicBarrierEntity.GROUND_MAX_RADIUS : MagicBarrierEntity.PERSONAL_MAX_RADIUS;
        double scaled = scopeRadius > 0 ? scopeRadius * (ground ? 1.5 : 0.6) : base;
        return Math.max(base, Math.min(max, scaled));
    }

    /** "teningr" -> CUBE, "flata" -> WALL, otherwise (including "kringlott", which is purely explicit-naming) -> SPHERE. If somehow both a cube and wall word are spoken together, WALL wins - a flat shape is a stronger, more specific instruction than a solid cube. */
    private static BarrierShape resolveShape(EffectInvocation invocation) {
        // Explicit geometry always wins. A generic varnbinda is a standing boundary/plane by
        // default, while skjoldr keeps its personal bubble default.
        if (hasWord(invocation, "flata") || hasWord(invocation, "frama") || hasWord(invocation, "aftana")) {
            return BarrierShape.WALL;
        }
        if (hasWord(invocation, "teningr")) return BarrierShape.CUBE;
        if (hasWord(invocation, "kringlott")) return BarrierShape.SPHERE;
        return isBoundaryMode(invocation) ? BarrierShape.WALL : BarrierShape.SPHERE;
    }

    /** "bur" - see MagicBarrierEntity.enforceExclusion() for what this actually changes. */
    private static boolean isCage(EffectInvocation invocation) {
        return hasWord(invocation, "bur");
    }

    /** "letta" spoken alongside "skjoldr" - release instead of cast. Checked before ristmark/mode dispatch in apply()/estimateBaseMagnitude(), since it's a completely different action, not a variant of casting. */
    private static boolean isDispel(EffectInvocation invocation) {
        return hasWord(invocation, "letta");
    }

    /** "aflbinda" - see MagicBarrierEntity.drainCasterStamina() for what this actually changes. */
    private static boolean isAflbound(EffectInvocation invocation) {
        return hasWord(invocation, "aflbinda");
    }

    /** "bur" without "ristmark": whatever LIVING entity the caster is currently looking at, within reach - the thing the cage is actually meant to trap. Null if nothing's there, in which case applyPersonal() falls back to centring on the caster (a cage around yourself is odd, but not refused). */
    private static LivingEntity resolveCageTarget(ServerPlayer caster) {
        Strikes.StrikeHit hit = Strikes.ray(caster, caster.getEyePosition(), caster.getLookAngle(), CAGE_TARGET_RANGE);
        return hit.entity() instanceof LivingEntity living ? living : null;
    }

    private static Vec3 horizontalFacing(ServerPlayer caster) {
        Vec3 flat = caster.getLookAngle().multiply(1, 0, 1);
        if (flat.lengthSqr() < 1.0e-8) {
            double yaw = Math.toRadians(caster.getYRot());
            flat = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        }
        return flat.normalize();
    }

    private static float shapeFactor(BarrierShape shape) {
        return switch (shape) {
            case WALL -> 0.7f; // a pane, not a volume - the cheapest shape
            case SPHERE -> 1.0f;
            case CUBE -> 1.15f; // more material at the same radius than a sphere (a cube's volume is larger for the same "radius" = half side length)
        };
    }

    private static String shapeNoun(BarrierShape shape) {
        return switch (shape) {
            case WALL -> "wall";
            case CUBE -> "cube";
            case SPHERE -> "bubble";
        };
    }

    private static MagicAffinity resolveAffinity(EffectInvocation invocation) {
        return MagicAffinity.resolve(invocation.composition().words());
    }
}
