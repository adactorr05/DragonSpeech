package com.dragonspeech.effect;

import com.dragonspeech.DragonSpeech;
import com.dragonspeech.engine.MagicAffinity;
import com.dragonspeech.weapon.ToolMaterial;
import com.dragonspeech.weapon.ToolType;
import com.dragonspeech.weapon.WeaponItems;
import com.dragonspeech.weapon.WeaponProjectileEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.Set;

/**
 * Backs the Weapon domain's hurl ladder (vopnkasta / vopnbinda) - "throw
 * a real tool/weapon at them (or at that spot) as a genuine flying,
 * embedding projectile." Named tool/weapon required (sverd/oxi/haki/
 * skofla/herfi/thrivoddr); a named material (jarn/gull/steinn/vidr/
 * demantr/svartmalmr) is OPTIONAL and defaults to wood, the weakest tier.
 *
 * Unlike BlockThrowEffectHandler (hurl_block), this spawns a REAL
 * WeaponProjectileEntity - see that class for why (built on AbstractArrow,
 * the same base vanilla's Trident uses, so flight/collision/sticking/
 * pickup are all proven vanilla behavior, not hand-rolled here).
 *
 * THREE WAYS THIS RESOLVES, all from the SAME sentence grammar the rest
 * of the mod already uses - no bespoke "rain" verb needed:
 *   - A bound ENTITY/BLOCK target (the normal look-target case): one
 *     projectile, aimed precisely at what's under the crosshair.
 *   - "marklaust" (DIRECTION target): one projectile, fired straight
 *     along the spoken/gaze direction - "vopnbinda oxi marklaust frama"
 *     genuinely throws an axe forward into open air.
 *   - An area scope word (viddum/umhverf/naerum -> multiple ENTITY
 *     targets): one projectile PER target, each dropped from directly
 *     above and aimed straight down - this is "rain of swords": just
 *     vopnbinda + sverd + an area scope word, exactly as spoken.
 *
 * SPEED WORDS: both modifier_magnitude words (mikla/litla/tvefalt/ofsa/
 * varla) AND tempo words (snoggt/seint) push damage and flight speed up
 * or down - see speedIntensity() below for why both families are read
 * here even though the rest of the mod normally keeps them separate.
 *
 * "taka" - DRAWING A REAL WEAPON VS CONJURING ONE: by default this
 * conjures a weapon out of nothing (costs more stamina - see
 * CONJURE_COST_MULTIPLIER - and can never be picked back up, since
 * there's no real item behind it). Speaking "taka" alongside the verb
 * asks the working to instead reach into the caster's own inventory (or
 * offhand) for a real matching item: if one is found, it's actually
 * removed from that inventory slot (preserving its durability/
 * enchantments) and thrown as itself - cheaper, and fully recoverable
 * afterward, exactly like it never left your hand. "taka" is a PROMISE,
 * not a preference: if nothing matching turns up, the working refuses
 * outright rather than quietly conjuring instead - checked once up
 * front (no real item anywhere -> the whole cast fails before anything
 * is targeted or thrown) and again per-throw in a multi-target "rain"
 * (which can still genuinely run OUT of real weapons partway through -
 * those later throws just don't happen, rather than being padded out
 * with conjured ones).
 *
 * "seida" is the explicit opposite of "taka" - purely a naming word, not
 * a behavior switch: NOT speaking "taka" already means conjure, so
 * "seida" doesn't need (or get) any special-case code here. It exists so
 * the sentence can SAY what it's doing ("vopnbinda sverd seida thetta" -
 * conjure and hurl a sword) instead of relying on an absence to imply
 * it - and, like any other correctly-guessed word, it raises the
 * sentence's average precision and so lowers its cost a little, exactly
 * like every other word in this language.
 */
public class HurlWeaponEffectHandler implements EffectHandler {

    private static final float MAX_DAMAGE = 28.0f;
    private static final double BASE_VELOCITY = 1.6;
    private static final double RAIN_DROP_HEIGHT = 12.0;

    /** Conjuring matter from nothing is wasteful compared to just throwing something you already own - this scales the STAMINA COST ESTIMATE only, never the actual thrown damage. */
    private static final float CONJURE_COST_MULTIPLIER = 1.6f;

    private static final EffectHandlerCaps CAPS = new EffectHandlerCaps(
        12, MAX_DAMAGE, 24f, Set.of(TargetKind.ENTITY, TargetKind.BLOCK, TargetKind.DIRECTION)
    );

    @Override
    public ResourceLocation id() {
        return DragonSpeech.id("hurl_weapon");
    }

    @Override
    public EffectHandlerCaps caps() {
        return CAPS;
    }

    @Override
    public float estimateBaseMagnitude(EffectInvocation invocation) {
        Optional<ToolType> namedTool = namedToolType(invocation);
        if (namedTool.isEmpty()) {
            return 1f; // no valid tool named - apply() will refuse; keep the cost estimate harmless
        }
        ToolMaterial material = namedMaterial(invocation).orElse(ToolMaterial.WOOD);
        MagicAffinity affinity = MagicAffinity.resolve(invocation.composition().words());
        boolean wantsDraw = spokeTaka(invocation);
        boolean magicalConstruct = !wantsDraw && affinity != MagicAffinity.ARCANE;
        float perThrow = computeDamage(namedTool.get(), material, affinity, magicalConstruct, speedIntensity(invocation));
        float total = perThrow * Math.max(invocation.targets().size(), 1);

        // A pure read of the caster's inventory - no mutation - just to
        // estimate whether this throw will likely draw a real item or
        // conjure one. EffectHandler.estimateBaseMagnitude's contract
        // explicitly allows reads, only forbids mutating game state.
        boolean willDraw = wantsDraw && hasMatchingItem(invocation.caster(), material, namedTool.get());
        return willDraw ? total : total * CONJURE_COST_MULTIPLIER;
    }

    @Override
    public EffectResult apply(EffectInvocation invocation) {
        Optional<ToolType> namedTool = namedToolType(invocation);
        if (namedTool.isEmpty()) {
            return EffectResult.failure(
                "The word reaches for a shape and finds none named - a hurling must name what it throws: a blade, an axe, some tool.");
        }
        ToolType toolType = namedTool.get();
        ToolMaterial material = namedMaterial(invocation).orElse(ToolMaterial.WOOD);
        boolean wantsDraw = spokeTaka(invocation);
        MagicAffinity affinity = MagicAffinity.resolve(invocation.composition().words());
        boolean magicalConstruct = !wantsDraw && affinity != MagicAffinity.ARCANE;

        // "taka" means "draw a REAL one, don't conjure" - if there isn't
        // one to draw, the working simply fails rather than quietly
        // falling back to conjuring (that fallback existed in an earlier
        // version of this system and is deliberately gone now - "taka"
        // is a promise about WHERE the weapon comes from, not just a
        // hint, and a broken promise should fail loudly, not paper over
        // itself). Checked once here, before any targeting/throwing, so
        // a doomed cast fails cleanly instead of partially happening.
        if (wantsDraw && !hasMatchingItem(invocation.caster(), material, toolType)) {
            return EffectResult.failure(
                "You reach for one you carry, but find none there - and the working will not conjure in its place.");
        }

        Optional<String> capViolation = CAPS.validateTargets(invocation);
        if (capViolation.isPresent()) {
            return EffectResult.failure(capViolation.get());
        }

        ServerPlayer caster = invocation.caster();
        if (!(caster.level() instanceof ServerLevel level)) {
            return EffectResult.failure("The working slips away.");
        }

        float speedSum = speedIntensity(invocation);
        float damage = computeDamage(toolType, material, affinity, magicalConstruct, speedSum);
        double velocity = BASE_VELOCITY * clamp(1f + speedSum * 0.5f, 0.5f, 2.0f);

        int thrown = 0;
        int drawnCount = 0;
        for (EffectTarget target : invocation.targets()) {
            ItemStack drawnStack = wantsDraw ? takeMatchingItem(caster, material, toolType).orElse(null) : null;
            if (wantsDraw && drawnStack == null) {
                // Ran out of real ones partway through a multi-target
                // "rain" - skip this one entirely rather than conjuring
                // to fill the gap; same "taka never conjures" rule.
                continue;
            }
            if (drawnStack != null) {
                drawnCount++;
            }

            switch (target) {
                case EffectTarget.OfEntity(Entity entity) -> {
                    // "Rain of swords": an area scope resolved several
                    // living targets at once - each gets its OWN
                    // projectile dropped from above rather than all of
                    // them sharing one bolt from the caster's hand.
                    boolean rain = invocation.targets().size() > 1;
                    if (rain) {
                        Vec3 from = entity.position().add(0, RAIN_DROP_HEIGHT, 0);
                        spawnProjectile(level, caster, material, toolType, affinity, damage, from, new Vec3(0, -1, 0), velocity, drawnStack);
                    } else {
                        Vec3 from = castingHandOrigin(caster);
                        Vec3 aim = entity.position().add(0, entity.getBbHeight() * 0.5, 0).subtract(from);
                        if (aim.lengthSqr() < 0.0001) aim = caster.getLookAngle();
                        spawnProjectile(level, caster, material, toolType, affinity, damage, from, aim.normalize(), velocity, drawnStack);
                    }
                    thrown++;
                }
                case EffectTarget.OfBlock(BlockPos pos) -> {
                    // "Fire an axe at that tree": aim straight at the
                    // targeted block's center so it embeds where you're
                    // actually looking (AbstractArrow's own onHitBlock
                    // handles the sticking-in - see WeaponProjectileEntity).
                    Vec3 from = castingHandOrigin(caster);
                    Vec3 aim = Vec3.atCenterOf(pos).subtract(from);
                    if (aim.lengthSqr() < 0.0001) aim = caster.getLookAngle();
                    spawnProjectile(level, caster, material, toolType, affinity, damage, from, aim.normalize(), velocity, drawnStack);
                    thrown++;
                }
                case EffectTarget.OfDirection(Vec3 origin, Vec3 direction) -> {
                    Vec3 from = castingHandOrigin(caster);
                    Vec3 gazePoint = origin.add(direction.normalize().scale(32.0));
                    Vec3 aim = gazePoint.subtract(from);
                    if (aim.lengthSqr() < 0.0001) aim = direction;
                    spawnProjectile(level, caster, material, toolType, affinity, damage, from, aim.normalize(), velocity, drawnStack);
                    thrown++;
                }
            }
        }

        if (thrown == 0) {
            return EffectResult.failure("There is nothing there to strike.");
        }
        String message;
        if (thrown > 1) {
            message = "Steel answers in a hail, and falls true.";
        } else if (drawnCount > 0) {
            message = affinity == MagicAffinity.ARCANE
                ? "You draw it from what you carry, and it flies true."
                : "You draw the real weapon, bind it in " + affinity.getSerializedName() + " magic, and cast it true.";
        } else if (magicalConstruct) {
            message = "A " + affinity.getSerializedName() + " " + toolType.getSerializedName() + " takes shape in your hand and flies true.";
        } else {
            message = "The bound weapon leaves your hand, and flies true.";
        }
        return EffectResult.success(damage, message);
    }

    /**
     * How "hard/fast" this hurl was spoken, combining TWO different word
     * families the rest of the mod keeps separate:
     *   - modifier_magnitude words (ofsa/tvefalt/mikla/litla/varla/hoegt) -
     *     intensity words that already drive most other handlers' damage.
     *   - tempo words (snoggt/seint) - normally only read by
     *     TemporalWorkingHandler for haste/slow bindings, but the design
     *     for this system explicitly calls for "snoggt" to hit harder and
     *     "seint" to hit softer too, exactly like a real thrown weapon's
     *     speed would matter. Weighted at 0.5 so one tempo word roughly
     *     matches one magnitude word's push rather than doubling up.
     */
    private static float speedIntensity(EffectInvocation invocation) {
        return invocation.modifierMagnitudeSum() + tempoSum(invocation) * 0.5f;
    }

    private static float tempoSum(EffectInvocation invocation) {
        float sum = 0f;
        for (com.dragonspeech.word.Word word : invocation.composition().words()) {
            sum += word.tempo();
        }
        return sum;
    }

    /** "taka" - same "check by true name" pattern CastRequestHandler already uses for "kringla"; no new Word schema field needed for a single flag word. */
    private static boolean spokeTaka(EffectInvocation invocation) {
        return invocation.composition().words().stream().anyMatch(w -> "taka".equals(w.trueName()));
    }

    /** Read-only check - is there a matching real item anywhere in the caster's inventory (main/hotbar or offhand)? Safe to call from estimateBaseMagnitude. */
    private static boolean hasMatchingItem(ServerPlayer caster, ToolMaterial material, ToolType toolType) {
        Item wanted = WeaponItems.canonicalItem(material, toolType);
        for (ItemStack stack : caster.getInventory().items) {
            if (!stack.isEmpty() && stack.is(wanted)) {
                return true;
            }
        }
        for (ItemStack stack : caster.getInventory().offhand) {
            if (!stack.isEmpty() && stack.is(wanted)) {
                return true;
            }
        }
        return false;
    }

    /** Actually removes ONE matching item from the caster's inventory (main/hotbar checked first, then offhand) and returns a 1-count copy of the REAL stack (durability/enchantments intact) - or empty if nothing matched. Only ever call this from apply(), after payment. */
    private static Optional<ItemStack> takeMatchingItem(ServerPlayer caster, ToolMaterial material, ToolType toolType) {
        Item wanted = WeaponItems.canonicalItem(material, toolType);
        for (ItemStack stack : caster.getInventory().items) {
            if (!stack.isEmpty() && stack.is(wanted)) {
                ItemStack taken = stack.copyWithCount(1);
                stack.shrink(1);
                return Optional.of(taken);
            }
        }
        for (ItemStack stack : caster.getInventory().offhand) {
            if (!stack.isEmpty() && stack.is(wanted)) {
                ItemStack taken = stack.copyWithCount(1);
                stack.shrink(1);
                return Optional.of(taken);
            }
        }
        return Optional.empty();
    }

    private void spawnProjectile(ServerLevel level, ServerPlayer caster, ToolMaterial material, ToolType toolType, MagicAffinity affinity,
                                  float damage, Vec3 from, Vec3 direction, double velocity, ItemStack drawnFromInventory) {
        if (toolType == ToolType.TRIDENT && affinity == MagicAffinity.ARCANE) {
            spawnTrident(level, caster, damage, from, direction, velocity, drawnFromInventory);
            return;
        }
        WeaponProjectileEntity projectile = new WeaponProjectileEntity(level, caster, material, toolType, damage, drawnFromInventory, affinity);
        projectile.setPos(from.x, from.y, from.z);
        projectile.shoot(direction.x, direction.y, direction.z, (float) velocity, 0f);
        level.addFreshEntity(projectile);
    }

    /**
     * Tridents ride on vanilla's OWN ThrownTrident entity/renderer
     * instead of our generic WeaponProjectileEntity - vanilla already
     * ships a purpose-built, correctly-oriented trident model and flight
     * renderer (ThrownTridentRenderer), which looks right immediately,
     * unlike our generic item-model approach (fine for tools with no
     * dedicated thrown-form model, but the trident already has one -
     * reusing it beats reinventing it).
     *
     * DAMAGE TRADEOFF, WORTH KNOWING: vanilla's ThrownTrident.onHitEntity
     * hard-codes its own base damage (8.0, before enchantments) and does
     * NOT read AbstractArrow's setBaseDamage() at all - it's simply
     * never consulted for tridents specifically. That means a thrown
     * trident here always deals vanilla's standard trident damage
     * (modified by any real enchantments if drawn via "taka"), not this
     * system's material-tier/speed-scaled formula the way every other
     * tool does. That's the price of reusing vanilla's exact, already-
     * polished visual rather than reimplementing it - the material
     * chosen still selects nothing (trident has no vanilla material
     * tiers anyway, see WeaponItems), only the speed words still matter
     * insofar as they affect thrown velocity, not damage.
     */
    private void spawnTrident(ServerLevel level, ServerPlayer caster, float damage, Vec3 from, Vec3 direction,
                               double velocity, ItemStack drawnFromInventory) {
        boolean drawn = drawnFromInventory != null && !drawnFromInventory.isEmpty();
        ItemStack stack = drawn ? drawnFromInventory.copy() : new ItemStack(Items.TRIDENT);

        ThrownTrident trident = new ThrownTrident(level, caster, stack);
        trident.setBaseDamage(damage); // no effect on tridents specifically - see method doc - kept for consistency/possible future vanilla changes
        trident.pickup = drawn ? AbstractArrow.Pickup.ALLOWED : AbstractArrow.Pickup.DISALLOWED;
        trident.setPos(from.x, from.y, from.z);
        trident.shoot(direction.x, direction.y, direction.z, (float) velocity, 0f);
        level.addFreshEntity(trident);
    }

    /** Base(tool) x material tier x speed - "less damage if thrown gently, more if thrown swift/hard," per the design. */
    private static float computeDamage(ToolType toolType, ToolMaterial material, MagicAffinity affinity, boolean magicalConstruct, float speedMagnitudeSum) {
        float speedMult = clamp(1f + speedMagnitudeSum * 0.5f, 0.3f, 2.5f);
        float substanceMult = magicalConstruct
            ? affinity.weaponDamageMultiplier()
            : material.damageMultiplier() * (affinity == MagicAffinity.ARCANE ? 1f : 1.05f);
        float damage = toolType.baseDamage() * substanceMult * speedMult;
        return clamp(damage, 1f, MAX_DAMAGE);
    }

    /** Same approximate right-hand origin used by the composed spell engine. */
    private static Vec3 castingHandOrigin(ServerPlayer caster) {
        Vec3 forward = caster.getLookAngle();
        if (forward.lengthSqr() < 1.0e-8) forward = new Vec3(0, 0, 1);
        forward = forward.normalize();
        Vec3 upWorld = new Vec3(0, 1, 0);
        Vec3 right = forward.cross(upWorld);
        if (right.lengthSqr() < 1.0e-6) {
            double yaw = Math.toRadians(caster.getYRot());
            Vec3 flatForward = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
            right = flatForward.cross(upWorld);
        }
        right = right.normalize();
        Vec3 up = right.cross(forward);
        if (up.lengthSqr() < 1.0e-6) up = upWorld; else up = up.normalize();
        return caster.getEyePosition().add(forward.scale(.62)).add(right.scale(.40)).subtract(up.scale(.32));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static Optional<ToolType> namedToolType(EffectInvocation invocation) {
        return invocation.composition().words().stream()
            .map(com.dragonspeech.word.Word::toolType)
            .flatMap(Optional::stream)
            .findFirst();
    }

    private static Optional<ToolMaterial> namedMaterial(EffectInvocation invocation) {
        return invocation.composition().words().stream()
            .map(com.dragonspeech.word.Word::toolMaterial)
            .flatMap(Optional::stream)
            .findFirst();
    }
}
