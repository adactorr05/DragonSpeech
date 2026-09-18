package com.dragonspeech.client.fx;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Visual-only hand/body anchors adapted from Dragon Flux. Server raycasts remain authoritative;
 * this only keeps spell bodies out of the camera and attached to the hand that appears to cast them.
 */
public final class SpellRenderAnchors {
    private static final Vec3 WORLD_UP = new Vec3(0, 1, 0);
    private SpellRenderAnchors() {}

    public static Vec3 rightHand(int ownerId, Vec3 fallback) { return hand(ownerId, fallback, false); }
    public static Vec3 leftHand(int ownerId, Vec3 fallback) { return hand(ownerId, fallback, true); }

    public static Vec3 hand(int ownerId, Vec3 fallback, boolean left) {
        Minecraft mc = Minecraft.getInstance();
        Entity owner = mc.level == null ? null : mc.level.getEntity(ownerId);
        if (owner == null) return fallback;
        Basis basis = basis(owner);
        if (owner == mc.player && mc.options.getCameraType().isFirstPerson()) {
            Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
            return camera.add(basis.forward.scale(.70))
                .add(basis.right.scale(left ? -.46 : .46))
                .add(basis.up.scale(-.34));
        }
        double side = left ? -1 : 1;
        double handSide = Math.max(.27, owner.getBbWidth() * .46 + .08);
        return renderPosition(owner).add(0, owner.getBbHeight() * .50, 0)
            .add(basis.horizontalForward.scale(.10))
            .add(basis.horizontalRight.scale(side * handSide));
    }

    public static Vec3 forward(int ownerId, Vec3 fallbackDirection) {
        Minecraft mc = Minecraft.getInstance();
        Entity owner = mc.level == null ? null : mc.level.getEntity(ownerId);
        return owner == null ? normalizeOr(fallbackDirection, new Vec3(0, 0, 1))
            : normalizeOr(owner.getLookAngle(), new Vec3(0, 0, 1));
    }

    public static Vec3 right(int ownerId) {
        Minecraft mc = Minecraft.getInstance();
        Entity owner = mc.level == null ? null : mc.level.getEntity(ownerId);
        return owner == null ? new Vec3(1, 0, 0) : basis(owner).right;
    }

    public static Vec3 up(int ownerId) {
        Minecraft mc = Minecraft.getInstance();
        Entity owner = mc.level == null ? null : mc.level.getEntity(ownerId);
        return owner == null ? WORLD_UP : basis(owner).up;
    }

    public static boolean localFirstPerson(int ownerId) {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.getId() == ownerId && mc.options.getCameraType().isFirstPerson();
    }

    private static Vec3 renderPosition(Entity owner) {
        Minecraft mc = Minecraft.getInstance();
        float partial = mc.getTimer().getGameTimeDeltaPartialTick(true);
        return owner.getPosition(partial);
    }

    private static Basis basis(Entity owner) {
        Vec3 forward = normalizeOr(owner.getLookAngle(), new Vec3(0, 0, 1));
        Vec3 horizontalForward = new Vec3(forward.x, 0, forward.z);
        if (horizontalForward.lengthSqr() < 1e-6) {
            double yaw = Math.toRadians(owner.getYRot());
            horizontalForward = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        } else horizontalForward = horizontalForward.normalize();
        Vec3 horizontalRight = horizontalForward.cross(WORLD_UP).normalize();
        Vec3 right = forward.cross(WORLD_UP);
        if (right.lengthSqr() < 1e-6) right = horizontalRight; else right = right.normalize();
        Vec3 up = right.cross(forward);
        if (up.lengthSqr() < 1e-6) up = WORLD_UP; else up = up.normalize();
        return new Basis(forward, right, up, horizontalForward, horizontalRight);
    }

    private static Vec3 normalizeOr(Vec3 value, Vec3 fallback) {
        return value != null && value.lengthSqr() > 1e-8 ? value.normalize() : fallback;
    }

    private record Basis(Vec3 forward, Vec3 right, Vec3 up, Vec3 horizontalForward, Vec3 horizontalRight) {}
}
