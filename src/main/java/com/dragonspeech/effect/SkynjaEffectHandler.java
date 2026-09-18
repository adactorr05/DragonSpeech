package com.dragonspeech.effect;

import com.dragonspeech.DragonSpeech;
import com.dragonspeech.entity.MagicBarrierEntity;
import com.dragonspeech.fx.SpellFx;
import com.dragonspeech.stamina.PlayerMagicData;
import com.dragonspeech.stamina.StaminaAccess;
import com.dragonspeech.storage.ItemStaminaStorage;
import com.dragonspeech.storage.StorageMediumRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Backs "skynja" - previously a pure prerequisite word with no
 * effect_handler at all, so casting it alone just failed with "this word
 * has no effect bound to it yet." Its own meaning ("to sense the stored
 * strength bound within a thing") is a perfectly good spell on its own,
 * and it's the natural sensing counterpart to draga (which it gates):
 * skynja tells you what's there before draga tries to draw on it.
 *
 * Self-targeting, read-only, and priced as a trivial sensing act (much
 * cheaper than actually moving any energy) - reads the stored charge in
 * your held item if you're holding a valid storage medium, or falls back
 * to reporting your own current stamina if you aren't holding one.
 */
public class SkynjaEffectHandler implements EffectHandler {

    private static final EffectHandlerCaps CAPS = new EffectHandlerCaps(
        1, 1f, 1f, Set.of(TargetKind.ENTITY)
    );

    @Override
    public ResourceLocation id() {
        return DragonSpeech.id("sense_stored_strength");
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
        return 1f; // sensing costs almost nothing - it changes nothing
    }

    @Override
    public EffectResult apply(EffectInvocation invocation) {
        ServerPlayer caster = invocation.caster();

        // skynja varn: the existing sensing verb applied to a barrier noun becomes Dragon
        // Flux-style barrier analysis without inventing a fixed Analyze ability.
        if (asksForBarrier(invocation)) {
            return analyzeBarrier(invocation, caster);
        }

        ItemStack held = caster.getMainHandItem();

        if (!held.isEmpty() && StorageMediumRegistry.isValidMedium(held.getItem())) {
            float stored = ItemStaminaStorage.storedIn(held);
            var props = StorageMediumRegistry.propertiesOf(held.getItem());
            float max = props.map(StorageMediumRegistry.MediumProperties::maxCapacity).orElse(stored);
            caster.sendSystemMessage(Component.literal(String.format(
                "You feel the strength bound within it: %.0f / %.0f.", stored, max)));
            return EffectResult.success(stored, "The word answers with a number, not a working.");
        }

        PlayerMagicData magic = StaminaAccess.get(caster);
        caster.sendSystemMessage(Component.literal(String.format(
            "You hold nothing that keeps strength - you feel only your own: %.0f / %.0f.",
            magic.stamina(), magic.maxStamina())));
        return EffectResult.success(magic.stamina(), "The word turns inward, finding only yourself.");
    }

    private static boolean asksForBarrier(EffectInvocation invocation) {
        return invocation.composition().words().stream().anyMatch(w ->
            "varn".equals(w.trueName()) || "varnbinda".equals(w.trueName()) || "skjoldr".equals(w.trueName()));
    }

    private static EffectResult analyzeBarrier(EffectInvocation invocation, ServerPlayer caster) {
        if (!(caster.level() instanceof ServerLevel level)) {
            return EffectResult.failure("The sensing word finds no stable boundary here.");
        }

        float spokenRadius = invocation.composition().scopeWord().map(w -> w.scopeRadius()).orElse(0f);
        if (spokenRadius > 0f) {
            double radius = Math.max(4.0, spokenRadius);
            List<MagicBarrierEntity> barriers = level.getEntitiesOfClass(MagicBarrierEntity.class,
                    caster.getBoundingBox().inflate(radius), b -> !b.isRemoved())
                .stream()
                .sorted(Comparator.comparingDouble(b -> b.distanceToSqr(caster)))
                .limit(8)
                .toList();
            if (barriers.isEmpty()) {
                return EffectResult.failure("No standing barrier answers within the reach you named.");
            }
            caster.sendSystemMessage(Component.literal("You sense " + barriers.size() + " standing barrier" + (barriers.size() == 1 ? "" : "s") + " nearby:"));
            for (MagicBarrierEntity barrier : barriers) sendBarrierReading(caster, barrier, level);
            return EffectResult.success(barriers.size(), "The boundaries answer as distinct knots of strength.");
        }

        var barrier = MagicBarrierEntity.findBarrierInSight(level, caster, 28.0, false);
        if (barrier.isEmpty()) {
            return EffectResult.failure("No barrier lies along your gaze to be read.");
        }
        sendBarrierReading(caster, barrier.get(), level);
        return EffectResult.success(1, "The barrier yields its structure to your sensing.");
    }

    private static void sendBarrierReading(ServerPlayer caster, MagicBarrierEntity barrier, ServerLevel level) {
        float max = Math.max(0.001f, barrier.maxStrength());
        int percent = Math.max(0, Math.min(100, Math.round((barrier.strength() / max) * 100f)));
        String owner = "unknown";
        if (barrier.casterId() != null) {
            ServerPlayer online = level.getServer().getPlayerList().getPlayer(barrier.casterId());
            owner = online != null ? online.getGameProfile().getName() : barrier.casterId().toString().substring(0, 8);
        }
        String lifecycle = barrier.isGround() ? "anchored" : (barrier.isStationary() ? "placed" : "carried");
        String behavior = barrier.cage() ? "cage" : "ward";
        String reflection = barrier.reflective() ? ", reflective" : "";

        caster.sendSystemMessage(Component.literal(String.format(
            "  %s %s %s — %.0f/%.0f strength (%d%%), radius %.1f, owner %s%s.",
            barrier.affinity().getSerializedName(), lifecycle, behavior, barrier.strength(), max, percent,
            barrier.radius(), owner, reflection)));
        caster.sendSystemMessage(Component.literal("    Counter-reading: " + barrier.affinity().analysisCounterHint() + "."));
        SpellFx.flash(level, barrier.color(), barrier.position());
    }

}
