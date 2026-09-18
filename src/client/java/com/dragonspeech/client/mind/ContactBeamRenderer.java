package com.dragonspeech.client.mind;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.List;

/**
 * Renders the Contact "reaching out" beam as a line of small gold dust
 * particles along the winding, mostly-ground-hugging path from the
 * player to the target (see BeamPath for the curve itself).
 *
 * REWRITTEN from raw custom geometry (a hand-built quad ribbon via
 * Tesselator/BufferBuilder) to vanilla's own particle system. That
 * earlier approach was the single highest-risk file in this whole mod -
 * it required matching an exact, unverifiable-without-a-compiler vertex-
 * builder API - and despite several rounds of fixes, it never rendered
 * reliably. This uses the EXACT same technique WardRingRenderer already
 * uses successfully elsewhere in this codebase
 * (client.level.addParticle(new DustParticleOptions(...), ...) from a
 * ClientTickEvents.END_CLIENT_TICK registration) - a real, proven,
 * low-risk pattern instead of a second, independent guess at a
 * version-sensitive rendering API. Per the user's own framing, this
 * beam is "more visual than anything" - reliability matters far more
 * here than pixel-perfect ribbon geometry.
 *
 * Particles are spawned fresh along the CURRENT path every tick (not
 * once at the start), so the line naturally follows the target if it
 * moves, without any special-case tracking logic - it's just recomputed
 * from live positions each time.
 */
public final class ContactBeamRenderer {

    private static final Vector3f GOLD = new Vector3f(0.95f, 0.75f, 0.25f);
    private static final float PARTICLE_SCALE = 0.55f;
    /** Every Nth path point gets a particle each tick - keeps particle count reasonable (a full path can have ~60 points) while still reading as a continuous line, since it's redrawn fresh every tick anyway. */
    private static final int POINT_STRIDE = 2;

    private ContactBeamRenderer() {}

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(ContactBeamRenderer::tick);
    }

    private static void tick(Minecraft client) {
        if (!ContactBeamState.isActive() || client.player == null || client.level == null) {
            return;
        }
        // Config GUI (Client tab) "Contact Beam Visual" - purely cosmetic; the reach/hasten mechanic
        // itself (ContactBeamState's own progress tracking) keeps running underneath regardless.
        if (!com.dragonspeech.client.config.DragonSpeechClientConfig.contactBeamVisible()) {
            return;
        }
        Entity target = client.level.getEntity(ContactBeamState.targetEntityId());
        if (target == null) {
            ContactBeamState.cancel();
            return;
        }

        // Clamped, NOT auto-canceled at 1.0 - the client's local progress
        // estimate is only a best-effort guess and can race ahead of the
        // server's actual resolution timing, especially with hasten. It
        // just holds fully-extended past that point and waits for the
        // server's explicit cancel (see ContactBeamPayload) to actually
        // stop spawning particles.
        float progress = Math.min(1f, ContactBeamState.progress());

        Vec3 fromFeet = client.player.position();
        Vec3 toFeet = target.position();
        double riseHeight = target.getBbHeight() * 0.7;

        List<Vec3> fullPath = BeamPath.build(fromFeet, toFeet, riseHeight, ContactBeamState.pathSeed());
        List<Vec3> visiblePath = BeamPath.truncate(fullPath, progress);

        for (int i = 0; i < visiblePath.size(); i += POINT_STRIDE) {
            Vec3 point = visiblePath.get(i);
            client.level.addParticle(new DustParticleOptions(GOLD, PARTICLE_SCALE),
                    point.x, point.y, point.z, 0.0, 0.0, 0.0);
        }
    }
}