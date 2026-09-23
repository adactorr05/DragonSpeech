package com.dragonspeech.client.ward;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.DustParticleOptions;
import org.joml.Vector3f;

/**
 * Draws each of the player's wards as a slowly-turning ring of colored
 * dust around them - one ring per ward, stacked upward, colored by what
 * the ward blocks, and brighter the more strength it still holds.
 *
 * CASTER-ONLY BY CONSTRUCTION: these particles are spawned client-side
 * via level.addParticle, which never leaves this machine - other players
 * cannot see your rings, and the server never even knows they're drawn.
 * (The server also only ever syncs a player their OWN wards.)
 *
 * VERSION-RISK NOTE: DustParticleOptions(Vector3f color, float scale) is
 * this file's touch point - if its constructor differs in your mappings,
 * check DustParticleOptions in the decompiled sources.
 */
public final class WardRingRenderer {

    private static final int PARTICLES_PER_RING = 10;
    private static int tick = 0;

    private WardRingRenderer() {}

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            tick++;
            if (tick % 5 != 0) {
                return; // four pulses a second reads as a steady ring without particle spam
            }
            render(client);
        });
    }

    private static void render(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null || client.level == null || ClientWardCache.get().isEmpty()) {
            return;
        }
        // Config GUI (Client tab) "Ward Ring Visual" - purely cosmetic; the ward mechanic itself is untouched by this.
        if (!com.dragonspeech.client.config.DragonSpeechClientConfig.wardRingVisible()) {
            return;
        }

        double spin = (tick / 5) * 0.09; // slow rotation

        int index = 0;
        for (ClientWardCache.WardVisual ward : ClientWardCache.get()) {
            Vector3f color = colorOf(ward.type());
            // Brilliance scales with remaining strength - a nearly-spent ward glows dim.
            float brightness = 0.35f + 0.65f * Math.max(0f, Math.min(1f, ward.fraction()));
            Vector3f scaled = new Vector3f(color.x * brightness, color.y * brightness, color.z * brightness);

            double radius = 0.9;
            double y = player.getY() + 0.4 + index * 0.35;
            double phase = spin + index * 0.7;

            for (int i = 0; i < PARTICLES_PER_RING; i++) {
                double angle = phase + (Math.PI * 2 * i / PARTICLES_PER_RING);
                client.level.addParticle(
                    new DustParticleOptions(scaled, 0.8f),
                    player.getX() + Math.cos(angle) * radius,
                    y,
                    player.getZ() + Math.sin(angle) * radius,
                    0, 0, 0
                );
            }
            index++;
        }
    }

    private static Vector3f colorOf(String type) {
        return switch (type) {
            case "projectile" -> new Vector3f(0.90f, 0.78f, 0.30f); // gold
            case "fire" -> new Vector3f(0.95f, 0.35f, 0.15f);       // ember red
            case "fall" -> new Vector3f(0.40f, 0.85f, 0.40f);       // green
            case "explosion" -> new Vector3f(0.95f, 0.55f, 0.15f);  // orange
            case "melee" -> new Vector3f(0.80f, 0.85f, 0.90f);      // pale steel
            case "magic" -> new Vector3f(0.35f, 0.90f, 0.48f);
            case "lightning" -> new Vector3f(0.65f, 0.82f, 1.00f); case "wind" -> new Vector3f(0.72f, 0.92f, 0.90f);
            case "ice" -> new Vector3f(0.45f, 0.80f, 1.00f); case "water" -> new Vector3f(0.20f, 0.52f, 0.95f);
            case "poison" -> new Vector3f(0.48f, 0.82f, 0.22f); case "force" -> new Vector3f(0.35f, 0.95f, 0.58f);
            case "earth" -> new Vector3f(0.55f, 0.38f, 0.20f); case "light" -> new Vector3f(1.00f, 0.95f, 0.64f);
            case "shadow" -> new Vector3f(0.26f, 0.18f, 0.36f); case "death" -> new Vector3f(0.36f, 0.15f, 0.25f);
            case "life" -> new Vector3f(0.30f, 0.95f, 0.50f); case "void" -> new Vector3f(0.25f, 0.12f, 0.55f);
            case "danger_lifskad" -> new Vector3f(0.78f,0.25f,0.32f); case "danger_lifrof" -> new Vector3f(0.72f,0.18f,0.40f);
            case "danger_lifslit" -> new Vector3f(0.62f,0.14f,0.50f); case "danger_lifstilla" -> new Vector3f(0.49f,0.12f,0.58f);
            case "danger_lifthagn" -> new Vector3f(0.33f,0.08f,0.48f);
            default -> new Vector3f(0.7f, 0.7f, 0.7f);
        };
    }
}
