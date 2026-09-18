package com.dragonspeech.entity;

import com.dragonspeech.DragonSpeech;
import com.dragonspeech.dragon.DragonEntity;
import com.dragonspeech.eldunari.DragonHeartVesselEntity;
import com.dragonspeech.elf.ElderElfEntity;
import com.dragonspeech.elf.ElfEntity;
import com.dragonspeech.human.HumanMageEntity;
import com.dragonspeech.mind.MindFortitudeService;
import com.dragonspeech.mind.SentienceTier;
import com.dragonspeech.shade.ShadeEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/**
 * Dragon Speech's entity types. The physical barrier skjoldr conjures,
 * plus (as of the dragons/Dragon Heart phase) the tameable/wild colored
 * dragon and the Dragon Heart's transient mind-duel vessel - see
 * com.dragonspeech.dragon.DragonEntity and
 * com.dragonspeech.eldunari.DragonHeartVesselEntity for what each
 * actually does; this file is registration only.
 *
 * DRAGON's dimensions are declared scalable so DragonAgeStage's
 * modelScale can resize a live entity via refreshDimensions() rather
 * than needing a different EntityType per age stage.
 *
 * WYVERN REMOVED FOR REAL THIS TIME. Last round only removed the
 * client-side renderer registration, not the EntityType registration
 * itself - so WYVERN was still a fully valid, registered entity type
 * with no renderer, which is WORSE than not registering it at all: a
 * completely unregistered entity ID in old save data gets skipped
 * gracefully with a log warning during chunk load, but a registered
 * type with a missing renderer crashes the very first time the game
 * tries to render one (exactly the NullPointerException in
 * EntityRenderDispatcher.shouldRender you hit). If your world has an
 * old wyvern entity saved in a chunk from earlier testing, this fix
 * makes it silently vanish on next load instead of crashing - that's
 * expected and fine, not something to worry about.
 */
public final class DragonSpeechEntities {

    public static final EntityType<MagicBarrierEntity> MAGIC_BARRIER = Registry.register(
        BuiltInRegistries.ENTITY_TYPE,
        DragonSpeech.id("magic_barrier"),
        FabricEntityTypeBuilder.<MagicBarrierEntity>create(MobCategory.MISC, MagicBarrierEntity::new)
            .dimensions(EntityDimensions.scalable(1.0f, 1.0f))
            .trackRangeChunks(8)
            .build()
    );

    public static final EntityType<DragonEntity> DRAGON = Registry.register(
        BuiltInRegistries.ENTITY_TYPE,
        DragonSpeech.id("dragon"),
        FabricEntityTypeBuilder.<DragonEntity>create(MobCategory.CREATURE, DragonEntity::new)
            // FIX: was still 6.0f/8.0f, the OLD custom dragon's own
            // hitbox size - stale, never updated for the new model at
            // all. 2.75f/2.75f matches both DragonEntity's own
            // BASE_WIDTH/BASE_HEIGHT constants AND Dragon Mounts
            // Legacy's own real, tested value for this exact model
            // (confirmed directly in their source, not guessed) - a
            // wrong hitbox here would affect actual collision/pathing,
            // not just look wrong.
            .dimensions(EntityDimensions.scalable(2.75f, 2.75f))
            .trackRangeChunks(10)
            .build()
    );

    /**
     * "Sub-hitboxes similar to the ender dragon." Separate, dedicated
     * type rather than reusing DRAGON's type (which is what
     * EnderDragonPart does, but that's only safe there because it's
     * never networked to the client at all - see DragonHitboxPart's
     * own javadoc for why reusing DRAGON's type here would crash the
     * client, since these ARE normally networked). No renderer is
     * registered for this type on the client - see
     * DragonSpeechClient's EntityRendererRegistry call, which uses a
     * no-op renderer so these stay invisible.
     */
    // FIX: DRAGON_HITBOX_PART removed - was crashing compilation
    // (DragonHitboxPart.java called dragon.hurtFromPart(), which
    // doesn't exist on the new entity at all). This whole multi-part
    // hitbox concept (separate head/body/tail/wing hitboxes, matching
    // the Ender Dragon's own design) was built around the OLD custom
    // model's specific proportions - Dragon Mounts Legacy's own
    // TameableDragon uses a single, simple hitbox with no multi-part
    // system at all. Removed to unblock compilation right now, not
    // silently dropped forever - if you still want per-limb hitboxes
    // on the new DML-style model, that's real, separate design work
    // (the new model's proportions/bone structure are quite different),
    // not something to guess at while just trying to get a build
    // working again.

    /**
     * The hurled-weapon system's real flying projectile (sverd/oxi/haki/
     * skofla/herfi/thrivoddr, in whatever material was named alongside
     * them) - see com.dragonspeech.weapon.WeaponProjectileEntity for why
     * this is built on AbstractArrow rather than hand-rolled. Dimensions
     * are small and roughly blade-shaped rather than block-shaped;
     * MobCategory.MISC matches every other non-living projectile/utility
     * entity in this file.
     */
    public static final EntityType<com.dragonspeech.weapon.WeaponProjectileEntity> WEAPON_PROJECTILE = Registry.register(
        BuiltInRegistries.ENTITY_TYPE,
        DragonSpeech.id("weapon_projectile"),
        FabricEntityTypeBuilder.<com.dragonspeech.weapon.WeaponProjectileEntity>create(MobCategory.MISC, com.dragonspeech.weapon.WeaponProjectileEntity::new)
            .dimensions(EntityDimensions.fixed(0.4f, 0.4f))
            .trackRangeChunks(6)
            .build()
    );

    public static final EntityType<DragonHeartVesselEntity> DRAGON_HEART_VESSEL = Registry.register(
        BuiltInRegistries.ENTITY_TYPE,
        DragonSpeech.id("dragon_heart_vessel"),
        FabricEntityTypeBuilder.<DragonHeartVesselEntity>create(MobCategory.MISC, DragonHeartVesselEntity::new)
            .dimensions(EntityDimensions.fixed(0.1f, 0.1f))
            .trackRangeChunks(6)
            .build()
    );

    /**
     * The four dynamic-spellcasting mobs (see com.dragonspeech.mob.casting for
     * the shared groundwork all of them build on). CREATURE for the neutral
     * two, MONSTER for the hostile Shade so it participates in the normal
     * hostile-mob spawn cap/despawn rules; Human Mage stays CREATURE even
     * though a given individual may roll hostile at spawn (see
     * HumanMageEntity) since the split is per-instance, not per-type.
     */
    public static final EntityType<ElfEntity> ELF = Registry.register(
        BuiltInRegistries.ENTITY_TYPE,
        DragonSpeech.id("elf"),
        FabricEntityTypeBuilder.<ElfEntity>create(MobCategory.CREATURE, ElfEntity::new)
            .dimensions(EntityDimensions.scalable(0.6f, 1.95f))
            .trackRangeChunks(8)
            .build()
    );

    public static final EntityType<ElderElfEntity> ELDER_ELF = Registry.register(
        BuiltInRegistries.ENTITY_TYPE,
        DragonSpeech.id("elder_elf"),
        FabricEntityTypeBuilder.<ElderElfEntity>create(MobCategory.CREATURE, ElderElfEntity::new)
            .dimensions(EntityDimensions.scalable(0.6f, 1.95f))
            .trackRangeChunks(8)
            .build()
    );

    public static final EntityType<HumanMageEntity> HUMAN_MAGE = Registry.register(
        BuiltInRegistries.ENTITY_TYPE,
        DragonSpeech.id("human_mage"),
        FabricEntityTypeBuilder.<HumanMageEntity>create(MobCategory.CREATURE, HumanMageEntity::new)
            .dimensions(EntityDimensions.scalable(0.6f, 1.95f))
            .trackRangeChunks(8)
            .build()
    );

    public static final EntityType<ShadeEntity> SHADE = Registry.register(
        BuiltInRegistries.ENTITY_TYPE,
        DragonSpeech.id("shade"),
        FabricEntityTypeBuilder.<ShadeEntity>create(MobCategory.MONSTER, ShadeEntity::new)
            .dimensions(EntityDimensions.scalable(0.6f, 1.95f))
            .trackRangeChunks(10)
            .build()
    );

    private DragonSpeechEntities() {}

    /** Call once from onInitialize() - referencing the class triggers the static registration above. */
    public static void bootstrap() {
        FabricDefaultAttributeRegistry.register(DRAGON, DragonEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(DRAGON_HEART_VESSEL, DragonHeartVesselEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(ELF, ElfEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(ELDER_ELF, ElderElfEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(HUMAN_MAGE, HumanMageEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(SHADE, ShadeEntity.createAttributes());

        // Sentience tiers for the mind-duel system (see SentienceTier's own
        // class comment, which already names "elves, skilled casters" as its
        // DISCIPLINED example). Human Mage sits one tier down (TRAINED) per
        // the design brief's "not as powerful as elves"; Shade uses CHAOTIC -
        // alien/unnatural rather than simply well-trained, same flavor as
        // endermen/the warden - which is also comfortably strong enough for
        // "vast amount of magic."
        MindFortitudeService.TIER_OVERRIDES.put(ELF, SentienceTier.DISCIPLINED);
        MindFortitudeService.TIER_OVERRIDES.put(ELDER_ELF, SentienceTier.DISCIPLINED);
        MindFortitudeService.TIER_OVERRIDES.put(HUMAN_MAGE, SentienceTier.TRAINED);
        MindFortitudeService.TIER_OVERRIDES.put(SHADE, SentienceTier.CHAOTIC);

        // DragonEntity and DragonHeartVesselEntity are always the hardest
        // minds in the game, regardless of instance-specific data (color,
        // age, bond state) - that per-instance scaling is handled by
        // MindScaling, not by the tier itself (see MindFortitudeService.
        // buildCombatant). This is the ONLY sentience wiring either class
        // needs - MobMindCombatAI already picks up any DRAGON-tier,
        // canActInDuel() entity automatically. Third-party/modded
        // dragon-tier entities go through com.dragonspeech.compat.
        // SentienceConfig instead of here - see that class.
        MindFortitudeService.TIER_OVERRIDES.put(DRAGON, SentienceTier.DRAGON);
        MindFortitudeService.TIER_OVERRIDES.put(DRAGON_HEART_VESSEL, SentienceTier.DRAGON);
    }
}
