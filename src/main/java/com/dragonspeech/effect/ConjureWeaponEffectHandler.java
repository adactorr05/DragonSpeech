package com.dragonspeech.effect;

import com.dragonspeech.DragonSpeech;
import com.dragonspeech.engine.MagicAffinity;
import com.dragonspeech.weapon.ConjuredWeaponItems;
import com.dragonspeech.weapon.ToolMaterial;
import com.dragonspeech.weapon.ToolType;
import com.dragonspeech.spell.SustainMode;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;
import java.util.Set;

/**
 * Implements direct creation with `seida`: when no other verb is present, `seida + element/material
 * + weapon` routes here and makes the construct into the caster's hand/inventory instead of firing it.
 * `seidabinda` remains as a backwards-compatible internal/action alias so existing learned-word data
 * is not invalidated; players do not need it for ordinary creation.
 */
public final class ConjureWeaponEffectHandler implements EffectHandler {

    private static final EffectHandlerCaps CAPS = new EffectHandlerCaps(
        1, 48f, 1f, Set.of(TargetKind.ENTITY)
    );

    @Override
    public ResourceLocation id() {
        return DragonSpeech.id("conjure_weapon");
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
        Optional<ToolType> tool = namedToolType(invocation);
        if (tool.isEmpty()) return 1f;
        ToolMaterial material = namedMaterial(invocation).orElse(ToolMaterial.WOOD);
        SustainMode mode = SustainMode.from(invocation.composition());
        float sustainScale = switch (mode) {
            case DURATION -> ConjuredWeaponItems.lifetimeTicks(invocation) / (float) ConjuredWeaponItems.BASE_LIFETIME_TICKS;
            case RESERVE -> ConjuredWeaponItems.reserveAmount(invocation, tool.get()) / 50f;
            case CASTER -> 1.15f;
        };
        return Math.min(48f, (4.5f + tool.get().baseDamage() * 0.35f) * material.damageMultiplier() * Math.max(.35f, sustainScale));
    }

    @Override
    public EffectResult apply(EffectInvocation invocation) {
        Optional<ToolType> toolLookup = namedToolType(invocation);
        if (toolLookup.isEmpty()) {
            return EffectResult.failure("The working can make nothing until you name its form - sword, axe, pick, shovel, hoe, spear, or trident.");
        }

        ServerPlayer caster = invocation.caster();
        ToolType tool = toolLookup.get();
        ToolMaterial material = namedMaterial(invocation).orElse(ToolMaterial.WOOD);
        ConjuredWeaponItems.Substance substance = ConjuredWeaponItems.resolveSubstance(invocation.composition().words());
        MagicAffinity affinity = substance.affinity();
        SustainMode mode = SustainMode.from(invocation.composition());
        int lifetime = ConjuredWeaponItems.lifetimeTicks(invocation);
        float reserve = ConjuredWeaponItems.reserveAmount(invocation, tool);
        ItemStack created = ConjuredWeaponItems.create(material, tool, affinity, substance.holographic(),
            caster.level().getGameTime(), lifetime, mode, caster.getUUID(), reserve);

        if (caster.getMainHandItem().isEmpty()) {
            caster.setItemInHand(InteractionHand.MAIN_HAND, created);
        } else {
            boolean placed = caster.getInventory().add(created);
            if (!placed && !created.isEmpty()) caster.drop(created, false);
        }

        String substanceName = substance.holographic()
            ? (affinity == MagicAffinity.ARCANE ? "pure magic" : affinity.getSerializedName())
            : material.getSerializedName();
        String sustain = switch (mode) {
            case DURATION -> "It will hold for about " + ConjuredWeaponItems.durationText(lifetime) + ".";
            case RESERVE -> "It carries its own reserve of " + Math.round(reserve) + " spell-strength.";
            case CASTER -> "It is bound directly to your stamina and will collapse when you can no longer sustain it.";
        };
        return EffectResult.success(mode == SustainMode.DURATION ? lifetime / 20f : reserve,
            "A " + substanceName + " " + tool.getSerializedName() + " takes form in your hand. " + sustain);
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
