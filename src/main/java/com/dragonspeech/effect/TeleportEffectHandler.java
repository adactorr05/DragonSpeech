package com.dragonspeech.effect;

import com.dragonspeech.DragonSpeech;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

/**
 * Backs the Motion domain's short-range shift ladder (hopa / vikja).
 * Self-targeting, like ChargeItemEffectHandler - this word is worked on
 * the caster, not on something they're looking at, so it ignores
 * invocation.targets() entirely and just moves invocation.caster().
 *
 * Deliberately does NOT attempt heimbinda ("recall home") here - that
 * word is left in the dictionary with no effect_handler for now. A real
 * "return to a known place" effect needs a stored location (bed/anchor
 * lookup) this pass doesn't build; wiring it to this same handler with a
 * fixed forward-blink would misrepresent what the word means, which
 * matters more than having every word be immediately castable.
 *
 * VERSION-RISK NOTE: Entity.setPos(x,y,z) is a plain reposition with no
 * collision/fall-damage handling and no "is this space solid" check, same
 * honesty caveat as this project's other handlers - a caster could blink
 * into a wall. A production version would want a short raycast/clear-space
 * check before committing the move; flagging that rather than silently
 * shipping it as solved.
 */
public class TeleportEffectHandler implements EffectHandler {

    private static final EffectHandlerCaps CAPS = new EffectHandlerCaps(
        1, 16f, 16f, Set.of(TargetKind.ENTITY)
    );

    @Override
    public ResourceLocation id() {
        return DragonSpeech.id("teleport");
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
        return 5.0f;
    }

    @Override
    public EffectResult apply(EffectInvocation invocation) {
        ServerPlayer caster = invocation.caster();

        float distance = 6f + (invocation.modifierMagnitudeSum() * 4f);
        distance = Math.max(2f, Math.min(distance, CAPS.maxMagnitudePerTarget()));

        Vec3 look = caster.getLookAngle();
        Vec3 forwardFlat = new Vec3(look.x, 0, look.z);
        forwardFlat = forwardFlat.lengthSqr() < 0.0001 ? new Vec3(0, 0, 1) : forwardFlat.normalize();

        Vec3 from = caster.position();
        Vec3 to = from.add(forwardFlat.scale(distance));

        caster.setPos(to.x, to.y, to.z);

        return EffectResult.success(distance, "The space between here and there forgets itself for a moment.");
    }
}
