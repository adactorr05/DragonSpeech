package com.dragonspeech.eldunari;

import com.dragonspeech.mind.MindDuelManager;
import com.dragonspeech.mind.MindScaling;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

/**
 * Renamed from EldunariVesselEntity - see DragonHeartState's own doc for
 * the rename's reasoning. Registry ID also changed, from
 * dragonspeech:eldunari_vessel to dragonspeech:dragon_heart_vessel - see
 * DragonSpeechEntities.
 *
 * Every resolver in the mind package (ContactResolver, MindDuelManager,
 * MindDuelActionService, MobMindCombatAI...) is actor-agnostic - they
 * only ever check "does this UUID match the attacker or defender," never
 * "is this a ServerPlayer" (see MobMindCombatAI's own javadoc, which
 * spells this out explicitly). That design is exactly what makes a
 * Dragon Heart possible without touching any of those files: a heart
 * item isn't a LivingEntity, so it can't be a duel participant directly
 * - this class is a real, if very short-lived, LivingEntity that STANDS
 * IN for the item's residual mind for the few seconds a duel attempt
 * takes, and is discarded the instant the duel ends (see
 * EldunariService, which listens for that end via
 * MindDuelService.END_LISTENERS and removes the vessel then).
 *
 * Deliberately: no AI goals, no gravity, no collision with anything, and
 * invulnerable to everything except being a mind-duel participant (which
 * doesn't route through the normal damage system at all - see
 * MindDuelService#onPhysicalDamage, which only touches Focus, not
 * health). It should be functionally invisible to a player who isn't
 * actively mid-duel with it.
 */
public class DragonHeartVesselEntity extends PathfinderMob implements MindScaling {

    /** Auto-removed after this many ticks even if nothing ever happens to it - a safety net in case a duel attempt is somehow abandoned without ever calling MindDuelService.end(). */
    private static final int MAX_LIFETIME_TICKS = 20 * 30;
    /** ContactResolver's own PendingContact travel delay tops out at BASE_DURATION_TICKS (60) for a player attacker - this needs to comfortably outlast that before concluding "contact never happened" for a vessel that hasn't duelled yet. */
    private static final int CONTACT_WINDOW_TICKS = 100;
    private int lifetime = 0;
    private boolean hasEverDuelled = false;

    /** Set once at spawn (see EldunariService#beginContact) - how "vast" this specific heart's mind is, independent of any living dragon. A freshly-broken-off heart is diminished, not a full living dragon's mind, but still DRAGON-tier in KIND. */
    private float mindPowerMultiplier = 0.6f;

    public DragonHeartVesselEntity(EntityType<? extends DragonHeartVesselEntity> type, Level level) {
        super(type, level);
        this.setInvulnerable(true);
        this.setInvisible(true);
        this.setNoGravity(true);
        this.setSilent(true);
        this.noPhysics = true;
        this.setNoAi(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 1.0);
    }

    public void setMindPowerMultiplier(float multiplier) {
        this.mindPowerMultiplier = multiplier;
    }

    @Override
    public float mindPowerMultiplier() {
        return mindPowerMultiplier;
    }

    @Override
    protected void registerGoals() {
        // Intentionally empty - a vessel has no body to move and no will
        // of its own beyond what MobMindCombatAI already grants any
        // DRAGON-tier canActInDuel() entity during the duel itself.
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide) {
            lifetime++;
            boolean stillInDuel = MindDuelManager.isInDuel(getUUID());
            if (stillInDuel) {
                hasEverDuelled = true;
            } else if (hasEverDuelled) {
                // Was in a duel, now isn't - it concluded. EldunariService's
                // END_LISTENERS hook already handled writing the outcome
                // back onto the item by the time this fires; this is just
                // cleanup.
                discard();
                return;
            } else if (lifetime > CONTACT_WINDOW_TICKS) {
                // Never entered a duel at all within the window contact
                // could plausibly take - the attempt failed or fizzled
                // (defender resisted, cooldown, etc). Nothing to write
                // back; the item just stays UNBONDED.
                discard();
                return;
            }
            if (lifetime > MAX_LIFETIME_TICKS) {
                discard();
            }
        }
    }

    @Override
    public boolean removeWhenFarAway(double distanceSquared) {
        return false; // lifetime/duel-state governs removal, not distance from any player
    }
}
