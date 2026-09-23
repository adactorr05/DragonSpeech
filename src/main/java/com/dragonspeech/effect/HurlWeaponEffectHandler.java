package com.dragonspeech.effect;

import com.dragonspeech.DragonSpeech;
import com.dragonspeech.engine.MagicAffinity;
import com.dragonspeech.weapon.ConjuredWeaponItems;
import com.dragonspeech.weapon.ToolMaterial;
import com.dragonspeech.weapon.ToolType;
import com.dragonspeech.spell.SustainMode;
import com.dragonspeech.weapon.WeaponItems;
import com.dragonspeech.weapon.WeaponProjectileEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.Set;

/**
 * Backs the Weapon domain's hurl ladder (vopnkasta / vopnbinda) - "throw
 * a real tool/weapon at them (or at that spot) as a genuine flying,
 * embedding projectile." Named tool/weapon required (sverd/oxi/haki/
 * skofla/herfi/voddr/thrivoddr); a named material (jarn/gull/steinn/vidr/
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
 * CONJURE_COST_MULTIPLIER). That construct now has a REAL temporary
 * ItemStack behind it for sustain/identity, but a weapon created specifically by this hurl is
 * ephemeral ammunition and cannot be picked up. Speaking "taka" alongside the verb
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
        Optional<ToolMaterial> requestedMaterial = namedMaterial(invocation);
        boolean wantsDraw = spokeTaka(invocation);
        ConjuredWeaponItems.Substance spokenSubstance = ConjuredWeaponItems.resolveSubstance(invocation.composition().words());
        MagicAffinity spokenAffinity = spokenSubstance.affinity();

        // If `taka` draws a weapon that was itself previously spoken into being, its stored affinity
        // survives the trip through inventory. If material was omitted, `taka` means the matching
        // tool already in hand/inventory rather than silently assuming wood.
        ItemStack preview = wantsDraw ? findMatchingItem(invocation.caster(), requestedMaterial, namedTool.get()).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
        ToolMaterial material = requestedMaterial.orElseGet(() -> inferMaterial(preview, namedTool.get()).orElse(ToolMaterial.WOOD));
        MagicAffinity affinity = spokenSubstance.holographic()
            ? spokenAffinity
            : ConjuredWeaponItems.affinity(preview).orElse(MagicAffinity.ARCANE);
        boolean conjuredMatter = !wantsDraw || ConjuredWeaponItems.isTemporary(preview);
        boolean magicalConstruct = conjuredMatter
            && (spokenSubstance.holographic() || ConjuredWeaponItems.isHolographic(preview));
        float perThrow = computeDamage(namedTool.get(), material, affinity, magicalConstruct, speedIntensity(invocation));
        float total = perThrow * Math.max(invocation.targets().size(), 1);

        // Drawing something already owned is cheaper than creating new matter. A temporary weapon
        // created earlier with `seida` still counts as drawn here - its creation was paid for
        // on the earlier cast and should not be charged a second time.
        boolean willDraw = wantsDraw && !preview.isEmpty();
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
        Optional<ToolMaterial> requestedMaterial = namedMaterial(invocation);
        boolean wantsDraw = spokeTaka(invocation);
        boolean weightless = spokeWeightless(invocation);
        ConjuredWeaponItems.Substance spokenSubstance = ConjuredWeaponItems.resolveSubstance(invocation.composition().words());
        MagicAffinity spokenAffinity = spokenSubstance.affinity();
        ItemStack drawPreview = wantsDraw
            ? findMatchingItem(invocation.caster(), requestedMaterial, toolType).orElse(ItemStack.EMPTY)
            : ItemStack.EMPTY;
        ToolMaterial material = requestedMaterial.orElseGet(() -> inferMaterial(drawPreview, toolType).orElse(ToolMaterial.WOOD));

        // "taka" means "draw a REAL one, don't conjure" - if there isn't
        // one to draw, the working simply fails rather than quietly
        // falling back to conjuring (that fallback existed in an earlier
        // version of this system and is deliberately gone now - "taka"
        // is a promise about WHERE the weapon comes from, not just a
        // hint, and a broken promise should fail loudly, not paper over
        // itself). Checked once here, before any targeting/throwing, so
        // a doomed cast fails cleanly instead of partially happening.
        if (wantsDraw && drawPreview.isEmpty()) {
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
        double velocity = BASE_VELOCITY * clamp(1f + speedSum * 0.5f, 0.5f, 2.0f);
        int conjuredLifetime = ConjuredWeaponItems.lifetimeTicks(invocation);
        SustainMode conjuredMode = SustainMode.from(invocation.composition());
        float conjuredReserve = ConjuredWeaponItems.reserveAmount(invocation, toolType);

        int thrown = 0;
        int drawnCount = 0;
        float lastDamage = 0f;
        for (EffectTarget target : invocation.targets()) {
            ItemStack weaponStack = wantsDraw
                ? takeMatchingItem(caster, requestedMaterial, toolType).orElse(null)
                : ConjuredWeaponItems.create(material, toolType, spokenAffinity, spokenSubstance.holographic(),
                    level.getGameTime(), conjuredLifetime, conjuredMode, caster.getUUID(), conjuredReserve);
            if (wantsDraw && weaponStack == null) {
                // Ran out of real ones partway through a multi-target
                // "rain" - skip this one entirely rather than conjuring
                // to fill the gap; same "taka never conjures" rule.
                continue;
            }
            if (wantsDraw) {
                drawnCount++;
            }

            boolean conjuredMatter = !wantsDraw || ConjuredWeaponItems.isTemporary(weaponStack);
            MagicAffinity affinity = spokenSubstance.holographic()
                ? spokenAffinity
                : ConjuredWeaponItems.affinity(weaponStack).orElse(MagicAffinity.ARCANE);
            boolean magicalConstruct = conjuredMatter
                && (spokenSubstance.holographic() || ConjuredWeaponItems.isHolographic(weaponStack));
            boolean recoverable = wantsDraw;
            float damage = computeDamage(toolType, material, affinity, magicalConstruct, speedSum);
            lastDamage = damage;

            switch (target) {
                case EffectTarget.OfEntity(Entity entity) -> {
                    // "Rain of swords": an area scope resolved several
                    // living targets at once - each gets its OWN
                    // projectile dropped from above rather than all of
                    // them sharing one bolt from the caster's hand.
                    boolean rain = invocation.targets().size() > 1;
                    if (rain) {
                        Vec3 from = entity.position().add(0, RAIN_DROP_HEIGHT, 0);
                        spawnProjectile(level, caster, material, toolType, affinity, damage, from, new Vec3(0, -1, 0), velocity, weaponStack, conjuredMatter, magicalConstruct, recoverable, weightless);
                    } else {
                        Vec3 from = castingHandOrigin(caster);
                        Vec3 aim = entity.position().add(0, entity.getBbHeight() * 0.5, 0).subtract(from);
                        if (aim.lengthSqr() < 0.0001) aim = caster.getLookAngle();
                        spawnProjectile(level, caster, material, toolType, affinity, damage, from, aim.normalize(), velocity, weaponStack, conjuredMatter, magicalConstruct, recoverable, weightless);
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
                    spawnProjectile(level, caster, material, toolType, affinity, damage, from, aim.normalize(), velocity, weaponStack, conjuredMatter, magicalConstruct, recoverable, weightless);
                    thrown++;
                }
                case EffectTarget.OfDirection(Vec3 origin, Vec3 direction) -> {
                    Vec3 from = castingHandOrigin(caster);
                    Vec3 gazePoint = origin.add(direction.normalize().scale(32.0));
                    Vec3 aim = gazePoint.subtract(from);
                    if (aim.lengthSqr() < 0.0001) aim = direction;
                    spawnProjectile(level, caster, material, toolType, affinity, damage, from, aim.normalize(), velocity, weaponStack, conjuredMatter, magicalConstruct, recoverable, weightless);
                    thrown++;
                }
            }
        }

        if (thrown == 0) {
            return EffectResult.failure("There is nothing there to strike.");
        }
        String message;
        if (thrown > 1) {
            message = wantsDraw ? "The weapons you carry answer in a hail and fall true." : "Conjured weapons answer in a hail and fall true.";
        } else if (drawnCount > 0) {
            message = "You draw the weapon you carry, and it flies true.";
        } else {
            MagicAffinity affinity = spokenAffinity;
            message = affinity == MagicAffinity.ARCANE
                ? "A temporary " + toolType.getSerializedName() + " takes shape for the cast and flies true; it will not remain to be claimed."
                : "A temporary " + affinity.getSerializedName() + " " + toolType.getSerializedName() + " takes shape for the cast and flies true; it will not remain to be claimed.";
        }
        return EffectResult.success(lastDamage, message);
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

    /** Gravity-language modifier: a hurled projectile with thyngdleysa ignores vanilla gravity. */
    private static boolean spokeWeightless(EffectInvocation invocation) {
        return invocation.composition().occurrencesOf("thyngdleysa") > 0;
    }

    /** Read-only lookup used for cost/affinity preview. Main hand is deliberately checked first. */
    private static Optional<ItemStack> findMatchingItem(ServerPlayer caster, Optional<ToolMaterial> material, ToolType toolType) {
        long now = caster.level().getGameTime();
        ItemStack main = caster.getMainHandItem();
        if (matchesRequested(main, material, toolType) && !ConjuredWeaponItems.isExpired(main, now)) return Optional.of(main);
        ItemStack off = caster.getOffhandItem();
        if (matchesRequested(off, material, toolType) && !ConjuredWeaponItems.isExpired(off, now)) return Optional.of(off);
        for (ItemStack stack : caster.getInventory().items) {
            if (stack == main) continue;
            if (matchesRequested(stack, material, toolType) && !ConjuredWeaponItems.isExpired(stack, now)) return Optional.of(stack);
        }
        return Optional.empty();
    }

    /** Removes one matching item, preferring the item actually held by the caster. */
    private static Optional<ItemStack> takeMatchingItem(ServerPlayer caster, Optional<ToolMaterial> material, ToolType toolType) {
        long now = caster.level().getGameTime();
        ItemStack main = caster.getMainHandItem();
        if (!ConjuredWeaponItems.dissolveIfExpired(main, now) && matchesRequested(main, material, toolType)) {
            ItemStack taken = main.copyWithCount(1);
            main.shrink(1);
            return Optional.of(taken);
        }
        ItemStack off = caster.getOffhandItem();
        if (!ConjuredWeaponItems.dissolveIfExpired(off, now) && matchesRequested(off, material, toolType)) {
            ItemStack taken = off.copyWithCount(1);
            off.shrink(1);
            return Optional.of(taken);
        }
        for (ItemStack stack : caster.getInventory().items) {
            if (stack == main) continue;
            if (ConjuredWeaponItems.dissolveIfExpired(stack, now)) continue;
            if (matchesRequested(stack, material, toolType)) {
                ItemStack taken = stack.copyWithCount(1);
                stack.shrink(1);
                return Optional.of(taken);
            }
        }
        return Optional.empty();
    }

    private static boolean matchesRequested(ItemStack stack, Optional<ToolMaterial> material, ToolType toolType) {
        if (stack == null || stack.isEmpty()) return false;
        if (material.isPresent()) return ConjuredWeaponItems.matches(stack, material.get(), toolType);
        if (ConjuredWeaponItems.isTemporary(stack)) {
            return ConjuredWeaponItems.toolType(stack).orElse(null) == toolType;
        }
        return WeaponItems.materialOf(stack, toolType).isPresent();
    }

    private static Optional<ToolMaterial> inferMaterial(ItemStack stack, ToolType toolType) {
        Optional<ToolMaterial> stored = ConjuredWeaponItems.material(stack);
        return stored.isPresent() ? stored : WeaponItems.materialOf(stack, toolType);
    }

    private void spawnProjectile(ServerLevel level, ServerPlayer caster, ToolMaterial material, ToolType toolType, MagicAffinity affinity,
                                  float damage, Vec3 from, Vec3 direction, double velocity, ItemStack weaponStack,
                                  boolean conjuredMatter, boolean holographic, boolean recoverable, boolean weightless) {
        // A genuinely-owned ordinary trident still gets vanilla's purpose-built thrown renderer.
        // Conjured tridents use our entity so their temporary-item lifetime and magical identity can
        // survive flight and pickup exactly like every other conjured weapon.
        if (toolType == ToolType.TRIDENT && affinity == MagicAffinity.ARCANE && !conjuredMatter) {
            spawnTrident(level, caster, damage, from, direction, velocity, weaponStack, weightless);
            return;
        }
        WeaponProjectileEntity projectile = new WeaponProjectileEntity(level, caster, material, toolType, damage, weaponStack, conjuredMatter, holographic, affinity);
        projectile.setRecoverable(recoverable);
        projectile.setNoGravity(weightless);
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
                               double velocity, ItemStack drawnFromInventory, boolean weightless) {
        boolean drawn = drawnFromInventory != null && !drawnFromInventory.isEmpty();
        ItemStack stack = drawn ? drawnFromInventory.copy() : new ItemStack(Items.TRIDENT);

        ThrownTrident trident = new ThrownTrident(level, caster, stack);
        trident.setBaseDamage(damage); // no effect on tridents specifically - see method doc - kept for consistency/possible future vanilla changes
        trident.pickup = drawn ? AbstractArrow.Pickup.ALLOWED : AbstractArrow.Pickup.DISALLOWED;
        trident.setNoGravity(weightless);
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
