package com.dragonspeech.client.weapon;

import com.dragonspeech.engine.MagicAffinity;
import com.dragonspeech.weapon.ConjuredWeaponItems;
import com.dragonspeech.weapon.ToolType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Dynamic item renderer for every spoken-into-being weapon/tool.  Inventory, dropped, GUI and hand
 * views use the exact same translucent elemental geometry as the projectile instead of falling back
 * to a vanilla sword/axe/pickaxe texture.
 */
public final class ConjuredWeaponItemRenderer implements BuiltinItemRendererRegistry.DynamicItemRenderer {

    private ConjuredWeaponItemRenderer() {}

    public static void register() {
        ConjuredWeaponItemRenderer renderer = new ConjuredWeaponItemRenderer();
        BuiltinItemRendererRegistry.INSTANCE.register(ConjuredWeaponItems.SWORD, renderer);
        BuiltinItemRendererRegistry.INSTANCE.register(ConjuredWeaponItems.AXE, renderer);
        BuiltinItemRendererRegistry.INSTANCE.register(ConjuredWeaponItems.PICKAXE, renderer);
        BuiltinItemRendererRegistry.INSTANCE.register(ConjuredWeaponItems.SHOVEL, renderer);
        BuiltinItemRendererRegistry.INSTANCE.register(ConjuredWeaponItems.HOE, renderer);
        BuiltinItemRendererRegistry.INSTANCE.register(ConjuredWeaponItems.SPEAR, renderer);
        BuiltinItemRendererRegistry.INSTANCE.register(ConjuredWeaponItems.TRIDENT, renderer);
    }

    @Override
    public void render(ItemStack stack, ItemDisplayContext mode, PoseStack poseStack,
                       MultiBufferSource vertexConsumers, int light, int overlay) {
        ToolType type = ConjuredWeaponItems.toolType(stack).orElse(ToolType.SWORD);
        MagicAffinity affinity = ConjuredWeaponItems.affinity(stack).orElse(MagicAffinity.ARCANE);

        poseStack.pushPose();
        // Geometry is authored lengthwise on local Z. These view transforms present that same real
        // 3-D construct cleanly in inventory, on the ground, and in either hand.
        switch (mode) {
            case GUI -> {
                poseStack.translate(0.5, 0.5, 0.5);
                poseStack.mulPose(Axis.XP.rotationDegrees(25f));
                poseStack.mulPose(Axis.YP.rotationDegrees(-40f));
                poseStack.mulPose(Axis.ZP.rotationDegrees(35f));
                poseStack.scale(0.72f, 0.72f, 0.72f);
            }
            case GROUND -> {
                poseStack.translate(0.5, 0.22, 0.5);
                poseStack.mulPose(Axis.XP.rotationDegrees(90f));
                poseStack.scale(0.52f, 0.52f, 0.52f);
            }
            case FIXED -> {
                poseStack.translate(0.5, 0.5, 0.5);
                poseStack.mulPose(Axis.YP.rotationDegrees(-90f));
                poseStack.scale(0.64f, 0.64f, 0.64f);
            }
            case FIRST_PERSON_LEFT_HAND, FIRST_PERSON_RIGHT_HAND -> {
                poseStack.translate(0.5, 0.45, 0.5);
                // The geometry's grip is centered near local Z=-0.56 and the blade/tool head points
                // toward +Z.  Put that grip at the hand origin first, then map +Z upward/outward.
                // The previous ~92 degree Z roll inverted the sword and left the hand intersecting
                // the middle of the blade.
                poseStack.mulPose(Axis.YP.rotationDegrees(mode == ItemDisplayContext.FIRST_PERSON_LEFT_HAND ? 10f : -10f));
                poseStack.mulPose(Axis.ZP.rotationDegrees(mode == ItemDisplayContext.FIRST_PERSON_LEFT_HAND ? -8f : 8f));
                poseStack.mulPose(Axis.XP.rotationDegrees(-90f));
                poseStack.translate(0.0, 0.0, 0.39);
                poseStack.scale(0.70f, 0.70f, 0.70f);
            }
            case THIRD_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND -> {
                poseStack.translate(0.5, 0.5, 0.5);
                poseStack.mulPose(Axis.YP.rotationDegrees(mode == ItemDisplayContext.THIRD_PERSON_LEFT_HAND ? 8f : -8f));
                poseStack.mulPose(Axis.ZP.rotationDegrees(mode == ItemDisplayContext.THIRD_PERSON_LEFT_HAND ? -10f : 10f));
                poseStack.mulPose(Axis.XP.rotationDegrees(-90f));
                poseStack.translate(0.0, 0.0, 0.35);
                poseStack.scale(0.62f, 0.62f, 0.62f);
            }
            default -> {
                poseStack.translate(0.5, 0.5, 0.5);
                poseStack.scale(0.65f, 0.65f, 0.65f);
            }
        }

        WeaponProjectileRenderer.drawMagicWeapon(poseStack.last().pose(), type, affinity);
        poseStack.popPose();
    }
}
