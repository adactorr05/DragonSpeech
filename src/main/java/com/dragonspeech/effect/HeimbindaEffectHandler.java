package com.dragonspeech.effect;

import com.dragonspeech.DragonSpeech;
import com.dragonspeech.fx.DragonSpeechParticles;
import com.dragonspeech.fx.SpellFx;
import com.dragonspeech.mind.HomeAnchor;
import com.dragonspeech.mind.HomeAnchorAttachments;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

/**
 * Backs "heimbinda" - previously had NO effect_handler at all (its
 * synonym_group pointing at "teleport" was cosmetic, not functional;
 * SpellCastResolver only reads effectHandlerId, so casting it just
 * failed with "this word has no effect bound to it yet"). Its own
 * meaning - "to bind oneself to a known place AND RETURN" - is
 * genuinely different from vikja's short blink, so rather than just
 * pointing it at the same handler, it gets its own: a home/recall anchor.
 *
 * GRAMMAR:
 *   heimbinda                - first cast: binds this place into memory.
 *                               Any LATER cast with no anchor override
 *                               recalls you here instead.
 *   heimbinda enda            - "end the old binding utterly": overwrites
 *                               your anchor with your current position,
 *                               even if one was already set. (enda is the
 *                               existing Truth-domain control word - see
 *                               EffectInvocation.composition().hasControlWord())
 *
 * Works across dimensions on purpose - a bound HOME should mean home,
 * not "wherever I happened to be in this dimension."
 *
 * This is deliberately just the anchor primitive. Linking two placed
 * marks into a two-way portal (ristmark + heimbinda, or a ring drawn
 * with hringr) is a natural next step built on the same anchor data, not
 * something this handler tries to also be.
 */
public class HeimbindaEffectHandler implements EffectHandler {

    private static final EffectHandlerCaps CAPS = new EffectHandlerCaps(
        1, 8f, 1f, Set.of(TargetKind.ENTITY)
    );

    @Override
    public ResourceLocation id() {
        return DragonSpeech.id("home_anchor");
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
        ServerPlayer caster = invocation.caster();
        boolean forceReset = invocation.composition().hasControlWord();
        boolean settingAnchor = forceReset || !HomeAnchorAttachments.get(caster).set();
        // Marking a place is cheap; being pulled across the world (or
        // between dimensions) back to it is real work.
        return settingAnchor ? 3f : 7f;
    }

    @Override
    public EffectResult apply(EffectInvocation invocation) {
        ServerPlayer caster = invocation.caster();
        if (!(caster.level() instanceof ServerLevel level)) {
            return EffectResult.failure("The working slips away.");
        }

        HomeAnchor current = HomeAnchorAttachments.get(caster);
        boolean forceReset = invocation.composition().hasControlWord();

        if (forceReset || !current.set()) {
            HomeAnchor anchor = new HomeAnchor(
                level.dimension().location().toString(),
                caster.getX(), caster.getY(), caster.getZ(), caster.getYRot(), true);
            HomeAnchorAttachments.set(caster, anchor);

            Vec3 fxPos = caster.position().add(0, 1, 0);
            SpellFx.burst(level, DragonSpeechParticles.SPARKLE, 0x2f9e8f, 0xffffff, fxPos, 14, 0.1);
            SpellFx.flash(level, 0x2f9e8f, fxPos);

            return EffectResult.success(1, current.set()
                ? "The old binding lets go, and a new one takes root here."
                : "You bind yourself to this place - speak the word again, from anywhere, to return to it.");
        }

        ResourceLocation dimensionLocation = ResourceLocation.tryParse(current.dimensionId());
        ServerLevel targetLevel = dimensionLocation != null
            ? caster.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, dimensionLocation))
            : null;

        if (targetLevel == null) {
            return EffectResult.failure(
                "The bound place no longer exists in any world that answers - speak heimbinda enda to bind anew.");
        }

        Vec3 originFx = caster.position().add(0, 1, 0);
        SpellFx.burst(level, DragonSpeechParticles.SPARKLE, 0x2f9e8f, 0xffffff, originFx, 14, 0.15);

        caster.teleportTo(targetLevel, current.x(), current.y(), current.z(), current.yaw(), caster.getXRot());

        Vec3 destFx = new Vec3(current.x(), current.y() + 1, current.z());
        SpellFx.flash(targetLevel, 0x2f9e8f, destFx);
        SpellFx.burst(targetLevel, DragonSpeechParticles.SPARKLE, 0x2f9e8f, 0xffffff, destFx, 14, 0.1);

        return EffectResult.success(1, "The word pulls taut, and the bound place draws you home.");
    }
}
