package com.dragonspeech.effect;

import com.dragonspeech.DragonSpeech;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Deliberately NOT a datapack-driven registry, unlike WordRegistry. Words
 * are safe to load from JSON because they can only ever select and
 * parameterize a handler that already exists in this map - they can never
 * add a new one. If this registry were data-driven too, that boundary
 * would disappear. Only register handlers here from compiled mod code
 * (yours or a trusted addon's ModInitializer), never from any player- or
 * server-operator-editable file.
 */
public final class EffectHandlerRegistry {

    private static final Map<ResourceLocation, EffectHandler> HANDLERS = new HashMap<>();

    private EffectHandlerRegistry() {}

    public static void register(EffectHandler handler) {
        if (HANDLERS.containsKey(handler.id())) {
            throw new IllegalStateException("Duplicate effect handler id: " + handler.id());
        }
        HANDLERS.put(handler.id(), handler);
        DragonSpeech.LOGGER.info("[DragonSpeech] Registered effect handler: {}", handler.id());
    }

    public static Optional<EffectHandler> get(ResourceLocation id) {
        return Optional.ofNullable(HANDLERS.get(id));
    }

    /** Registers the base primitive handlers. Call once from onInitialize(). */
    public static void bootstrap() {
        register(new IgniteEffectHandler());
        register(new PushEffectHandler());
        register(new HealEffectHandler());
        register(new ChanneledPushEffectHandler());
        register(new ChargeItemEffectHandler());
        // REDESIGNED: "gala" is now the single verb for the whole enchantment system - see ApplyEnchantEffectHandler's own doc for the full grammar and why the old per-word handlers below are no longer registered individually.
        register(new com.dragonspeech.effect.ApplyEnchantEffectHandler());

        register(new FreezeEffectHandler());
        register(new SunderEffectHandler());
        register(new LiftEffectHandler());
        register(new ResurrectEffectHandler());
        register(new ConfuseEffectHandler());
        register(new ShockEffectHandler());
        register(new PoisonEffectHandler());
        register(new TeleportEffectHandler());
        register(new PetrifyEffectHandler());
        register(new SummonEffectHandler());
        register(new ShapeBlockEffectHandler());
        register(new PillarEffectHandler());
        register(new WallEffectHandler());
        register(new BlockThrowEffectHandler());
        // vopnkasta/vopnbinda - real flying, embedding weapon projectiles
        // (sword/axe/tool, in whatever material was named). See the
        // weapon package.
        register(new HurlWeaponEffectHandler());
        // seidabinda - creates a real temporary weapon item whose lifetime is paid for with magnitude words.
        register(new ConjureWeaponEffectHandler());
        // The composed elemental spell system: ONE handler, many forms -
        // every form verb (kasta/geisla/kula/sprengja/hringr/skyja/
        // umljomi/ristmark/regnfalla) and element-verb (eldingkast, ...)
        // routes here; the sentence decides the rest. See the engine package.
        register(new com.dragonspeech.engine.ElementalWorkingHandler());
        // Time bindings (tidbinda/kyrra) - haste, slow, and full stasis. See engine package.
        register(new com.dragonspeech.engine.TemporalWorkingHandler());
        // hambinda - EBW-style possession: wear a creature's skin. See mind package.
        register(new PossessEffectHandler());
        // skynja - reads stored charge (a held item's, or your own stamina). Previously unwired.
        register(new SkynjaEffectHandler());
        // heimbinda - set/recall a personal home anchor. Previously unwired.
        register(new HeimbindaEffectHandler());
        // aflsuga/aflflyta - drain a target's strength, optionally into your own stamina.
        register(new DrainStaminaEffectHandler());
        // lifssuga/lifflyta - drain a target's life, optionally into your own health.
        register(new DrainLifeEffectHandler());
        // skjoldr - a real, physical, visible barrier. See entity package.
        register(new BarrierEffectHandler());
        // thyngja/thyngdbinda - real per-tick gravity manipulation. See com.dragonspeech.engine.GravityFieldManager.
        register(new GravityScaleEffectHandler());
        // marka - a lasting good/bad tag, currently read by MagicBarrierEntity's ward/cage exclusion. See com.dragonspeech.engine.MarkRegistry.
        register(new MarkEffectHandler());
        register(new DangerWordEffectHandler());
    }
}
