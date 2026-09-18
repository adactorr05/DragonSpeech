package com.dragonspeech.mind;

import com.dragonspeech.DragonSpeech;
import com.dragonspeech.fx.DragonSpeechParticles;
import com.dragonspeech.fx.SpellFx;
import com.dragonspeech.network.DragonSpeechNetworking;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.FlyingMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * EBW-STYLE POSSESSION (hambinda): the caster binds themself INTO the
 * mob's skin - one body, worn. This is deliberately a different working
 * from MindControlService's Control (hugbinda -> duel -> remote
 * puppeteering, camera projected while your body stands behind):
 * hambinda takes you to the creature, moves it with your own native
 * controls, lends you its strength, and makes its flesh your armor.
 *
 * Ported behavior from Electroblob's Wizardry 1.12.2 Possession.java,
 * adapted to a server-authoritative Fabric 1.21.1 world:
 *
 *   EBW removed the mob from the world, ticked it manually, and rendered
 *   the mob's model on the player. Here instead the mob STAYS in the
 *   world - noAI, noPhysics, silent, pinned to the player every tick -
 *   so everyone else naturally sees the mob walking around (that's EBW's
 *   render swap, achieved server-side), while the player is hidden under
 *   an ambient invisibility effect and, on their own screen, the mob is
 *   render-skipped in first person (PossessedMobRenderMixin).
 *
 * Faithful pieces, one for one with EBW:
 *  - teleport to the mob; its position becomes yours
 *  - attribute inheritance: movement speed, attack damage, knockback
 *    resistance multiplied to the mob's values (skipped per-attribute if
 *    the mob's gear provides it, same as EBW's zombie-pigman guard)
 *  - creative-style flight while possessing a flying creature
 *  - your inventory is set aside (persistently - see
 *    PossessionAttachments) and you get the mob's weapon; bows arrive
 *    with Infinity and an arrow, strays give tipped slowness arrows
 *  - nearby mobs lose track of you ("Huh?! Where'd you go?"), and mobs
 *    that wouldn't attack your borrowed shape keep ignoring you
 *  - damage aimed at you is taken by the mob's body first (its armor,
 *    its immunities), and you feel HALF of what gets through; if that
 *    would drop you to critical health, you're set to critical and
 *    thrown out of the skin
 *  - per-creature abilities and ranged attacks - PossessionAbilities
 *  - ends on sneak, on the timer, on the mob's death, on your death or
 *    logout - always restoring both bodies and your inventory
 */
public final class PossessionService {

    /** You are ejected (never killed) when redirected damage would take you to or below this. */
    public static final float CRITICAL_HEALTH = 2.0f;
    private static final float REDIRECTED_DAMAGE_FRACTION = 0.5f;
    private static final double AGGRO_CLEAR_RADIUS = 16.0;

    private static final Map<UUID, Possession> BY_PLAYER = new HashMap<>();
    private static final Map<UUID, UUID> PLAYER_BY_MOB = new HashMap<>();

    /** Reentrancy guard: our own redirected/half damage must not be re-redirected. */
    private static boolean applyingInternalDamage = false;

    private static final Map<Holder<net.minecraft.world.entity.ai.attributes.Attribute>, ResourceLocation> INHERITED_ATTRIBUTES = Map.of(
        Attributes.MOVEMENT_SPEED, DragonSpeech.id("possession_speed"),
        Attributes.ATTACK_DAMAGE, DragonSpeech.id("possession_attack"),
        Attributes.KNOCKBACK_RESISTANCE, DragonSpeech.id("possession_knockback")
    );

    record Possession(UUID playerId, UUID mobId, long expiresAtTick,
                      boolean mobHadNoAi, boolean grantedFlight,
                      ItemStack grantedMainHand, ItemStack grantedOffHand) {}

    private PossessionService() {}

    // ============================== Registration ==============================

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(PossessionService::tick);

        // Damage aimed at a possessing player is taken by the borrowed body
        // instead (its armor and immunities apply); the player then feels
        // half of whatever actually got through - see onMobDamaged.
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (applyingInternalDamage) {
                return true;
            }
            if (entity instanceof ServerPlayer player && isPossessing(player)) {
                // The void reaches through any skin, exactly as in EBW.
                if (source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
                    return true;
                }
                Mob mob = possessedMob(player);
                if (mob != null) {
                    applyingInternalDamage = true;
                    try {
                        mob.hurt(source, amount);
                    } finally {
                        applyingInternalDamage = false;
                    }
                }
                return false;
            }
            return true;
        });

        // The possessor shares the borrowed body's pain (post-armor).
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseAmount, actualAmount, blocked) -> {
            if (applyingInternalDamage || blocked || !(entity instanceof Mob mob)) {
                return;
            }
            UUID playerId = PLAYER_BY_MOB.get(mob.getUUID());
            if (playerId == null || mob.getServer() == null) {
                return;
            }
            ServerPlayer player = mob.getServer().getPlayerList().getPlayer(playerId);
            if (player == null) {
                return;
            }

            applyingInternalDamage = true;
            try {
                float shared = actualAmount * REDIRECTED_DAMAGE_FRACTION;
                if (!player.isCreative() && shared > 0) {
                    player.hurt(player.damageSources().generic(), shared);
                }
            } finally {
                applyingInternalDamage = false;
            }

            if (player.getHealth() <= CRITICAL_HEALTH && !player.isCreative()) {
                player.setHealth(CRITICAL_HEALTH);
                end(player, "The pain tears you out of the borrowed skin.");
            }
        });

        // The borrowed body dying throws the possessor out of it.
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof Mob mob) {
                UUID playerId = PLAYER_BY_MOB.get(mob.getUUID());
                if (playerId != null && mob.getServer() != null) {
                    ServerPlayer player = mob.getServer().getPlayerList().getPlayer(playerId);
                    if (player != null) {
                        end(player, "The skin dies around you, and you are cast back into your own.");
                    }
                }
            }
            // The possessor dying releases the skin, so items drop correctly.
            if (entity instanceof ServerPlayer player && isPossessing(player)) {
                end(player, null);
            }
        });

        // Logging out always releases; logging in restores any inventory a
        // crash mid-possession left set aside (see PossessionAttachments).
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            if (isPossessing(handler.getPlayer())) {
                end(handler.getPlayer(), null);
            }
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
            PossessionAttachments.restoreIfOrphaned(handler.getPlayer()));

        PossessionAbilities.register();
    }

    // ============================== Queries ==============================

    public static boolean isPossessing(ServerPlayer player) {
        return BY_PLAYER.containsKey(player.getUUID());
    }

    public static boolean isPossessed(UUID mobId) {
        return PLAYER_BY_MOB.containsKey(mobId);
    }

    /** The mob whose skin this player currently wears, or null. */
    public static Mob possessedMob(ServerPlayer player) {
        Possession possession = BY_PLAYER.get(player.getUUID());
        if (possession == null || !(player.level() instanceof ServerLevel level)) {
            return null;
        }
        return level.getEntity(possession.mobId()) instanceof Mob mob ? mob : null;
    }

    // ============================== Start ==============================

    public static boolean start(ServerPlayer player, Mob target, int durationTicks) {
        if (isPossessing(player) || isPossessed(target.getUUID()) || player.isShiftKeyDown()) {
            return false;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }

        // Set the caster's inventory aside - persistently, so even a crash
        // mid-possession can never destroy it.
        PossessionAttachments.saveInventory(player);
        player.getInventory().clearContent();

        // ...and take up the creature's weapon, EBW-style.
        ItemStack mainHand = PossessionAbilities.grantedMainHand(target, level);
        ItemStack offHand = PossessionAbilities.grantedOffHand(target);
        player.setItemSlot(EquipmentSlot.MAINHAND, mainHand.copy());
        if (!offHand.isEmpty()) {
            player.setItemSlot(EquipmentSlot.OFFHAND, offHand.copy());
        }

        // Step into the skin.
        boolean mobHadNoAi = target.isNoAi();
        player.teleportTo(level, target.getX(), target.getY(), target.getZ(), target.getYRot(), target.getXRot());

        target.stopRiding();
        target.ejectPassengers();
        target.setNoAi(true);
        target.setSilent(true);
        target.noPhysics = true; // no block collision, and Entity.push() skips noPhysics entities - so the two co-located bodies never shove each other
        target.setTarget(null);
        target.setPersistenceRequired();

        // Borrow its strength: speed, attack, knockback resistance scaled
        // to the creature's values (skipping any the creature only has
        // from its gear - EBW's zombie-pigman one-hit guard).
        inheritAttributes(player, target);

        // Borrow its wings.
        boolean flight = false;
        if ((target instanceof FlyingMob || target.getNavigation() instanceof FlyingPathNavigation)
            && !player.getAbilities().mayfly) {
            player.getAbilities().mayfly = true;
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
            flight = true;
        }

        // Nearby creatures lose track of the vanished player.
        for (Mob nearby : level.getEntitiesOfClass(Mob.class,
            new AABB(player.position(), player.position()).inflate(AGGRO_CLEAR_RADIUS))) {
            if (nearby.getTarget() == player && !nearby.canAttackType(target.getType())) {
                nearby.setTarget(null);
            }
        }

        // The player's own shape is veiled; everyone simply sees the mob.
        player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, durationTicks + 20, 0, true, false));

        BY_PLAYER.put(player.getUUID(), new Possession(player.getUUID(), target.getUUID(),
            level.getServer().getTickCount() + durationTicks, mobHadNoAi, flight,
            mainHand.copy(), offHand.copy()));
        PLAYER_BY_MOB.put(target.getUUID(), player.getUUID());

        // On the possessor's own screen the mob is render-skipped in first
        // person so the borrowed skin doesn't sit inside the camera.
        DragonSpeechNetworking.sendPossessionState(player, target.getId(), true);

        Vec3 fxPos = target.position().add(0, target.getBbHeight() * 0.5, 0);
        SpellFx.burst(level, DragonSpeechParticles.DARK_MAGIC, 0x2a1040, 0x0e0413, fxPos, 18, 0.15);
        SpellFx.flash(level, 0x8a6bb8, fxPos);
        return true;
    }

    private static void inheritAttributes(ServerPlayer player, Mob target) {
        attributes:
        for (var entry : INHERITED_ATTRIBUTES.entrySet()) {
            AttributeInstance mobInstance = target.getAttribute(entry.getKey());
            AttributeInstance playerInstance = player.getAttribute(entry.getKey());
            if (mobInstance == null || playerInstance == null) {
                continue;
            }

            // If the creature only has this from its equipment, don't
            // inherit it - the equipment itself was already handed over.
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                ItemStack gear = target.getItemBySlot(slot);
                if (!gear.isEmpty() && gearProvidesAttribute(gear, entry.getKey(), slot)) {
                    continue attributes;
                }
            }

            double targetValue = mobInstance.getValue();
            double currentValue = playerInstance.getValue();
            if (currentValue <= 0 || targetValue <= 0) {
                continue;
            }

            playerInstance.removeModifier(entry.getValue());
            playerInstance.addTransientModifier(new AttributeModifier(
                entry.getValue(), targetValue / currentValue - 1.0,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    /**
     * VERSION-RISK NOTE: 1.21.1 moved item attribute modifiers into the
     * ATTRIBUTE_MODIFIERS data component. This walks that component's
     * entries; if the component's record shape shifted in your mappings,
     * this method (a conservative guard, nothing more) is safe to reduce
     * to `return false` - the only cost is slightly stronger possessed
     * gear-mobs, EBW's original edge case.
     */
    private static boolean gearProvidesAttribute(ItemStack gear,
                                                 Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
                                                 EquipmentSlot slot) {
        var modifiers = gear.get(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS);
        if (modifiers == null) {
            return false;
        }
        for (var entry : modifiers.modifiers()) {
            if (entry.attribute().equals(attribute) && entry.slot().test(slot)) {
                return true;
            }
        }
        return false;
    }

    // ============================== Tick ==============================

    private static void tick(MinecraftServer server) {
        if (BY_PLAYER.isEmpty()) {
            return;
        }
        long now = server.getTickCount();

        for (Possession possession : List.copyOf(BY_PLAYER.values())) {
            ServerPlayer player = server.getPlayerList().getPlayer(possession.playerId());
            if (player == null) {
                continue; // disconnect handler will have cleaned up / will clean up
            }

            Mob mob = possessedMob(player);
            if (mob == null || !mob.isAlive() || now >= possession.expiresAtTick()) {
                end(player, mob == null || !mob.isAlive()
                    ? "The skin dies around you, and you are cast back into your own."
                    : "The binding frays, and the skin sheds you.");
                continue;
            }

            // Sneak sheds the skin deliberately, exactly like EBW.
            if (player.isShiftKeyDown()) {
                end(player, "You let the skin go.");
                continue;
            }

            // The borrowed body rides the player, every tick: position,
            // facing, velocity - so its animations, its hitbox, and what
            // everyone else sees all follow the player's own movement.
            mob.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
            mob.setYHeadRot(player.getYHeadRot());
            mob.yBodyRot = player.yBodyRot;
            mob.setDeltaMovement(player.getDeltaMovement());
            mob.fallDistance = 0;
            mob.getNavigation().stop();
            mob.setTarget(null);

            PossessionAbilities.tickAbilities(player, mob);

            // Creatures keep ignoring the vanished player, as long as they
            // wouldn't attack the shape they're wearing.
            if (now % 20 == 0 && player.level() instanceof ServerLevel level) {
                for (Mob nearby : level.getEntitiesOfClass(Mob.class,
                    new AABB(player.position(), player.position()).inflate(AGGRO_CLEAR_RADIUS))) {
                    if (nearby != mob && nearby.getTarget() == player && !nearby.canAttackType(mob.getType())) {
                        nearby.setTarget(null);
                    }
                }

                // A quiet dark shimmer around the possessor, EBW's tell.
                SpellFx.of(DragonSpeechParticles.DARK_MAGIC)
                    .pos(0, mob.getBbHeight() * 0.5, 0).entity(mob)
                    .color(0x1a0033).time(20).scale(0.8f)
                    .spawn(level);
            }
        }
    }

    // ============================== End ==============================

    /** Releases the skin and restores both bodies. Safe to call from anywhere; no-op if not possessing. `message` may be null. */
    public static void end(ServerPlayer player, String message) {
        Possession possession = BY_PLAYER.remove(player.getUUID());
        if (possession == null) {
            return;
        }
        PLAYER_BY_MOB.remove(possession.mobId());

        // Restore the creature.
        if (player.level() instanceof ServerLevel level
            && level.getEntity(possession.mobId()) instanceof Mob mob && mob.isAlive()) {
            mob.setNoAi(possession.mobHadNoAi());
            mob.setSilent(false);
            mob.noPhysics = false;
            mob.setDeltaMovement(Vec3.ZERO);

            Vec3 fxPos = mob.position().add(0, mob.getBbHeight() * 0.5, 0);
            SpellFx.burst(level, DragonSpeechParticles.DARK_MAGIC, 0x2a1040, 0x0e0413, fxPos, 12, 0.12);
        }

        // Restore the player: attributes, flight, veil.
        for (var entry : INHERITED_ATTRIBUTES.entrySet()) {
            AttributeInstance instance = player.getAttribute(entry.getKey());
            if (instance != null) {
                instance.removeModifier(entry.getValue());
            }
        }
        if (possession.grantedFlight() && !player.isCreative()) {
            player.getAbilities().mayfly = false;
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
        }
        player.removeEffect(MobEffects.INVISIBILITY);

        // Inventory: the conjured weapon dissolves, anything genuinely
        // picked up while possessing is dropped at your feet (never
        // silently destroyed), and your own things return to your hands.
        removeIfStillGranted(player, EquipmentSlot.MAINHAND, possession.grantedMainHand());
        removeIfStillGranted(player, EquipmentSlot.OFFHAND, possession.grantedOffHand());
        dropLeftovers(player);
        PossessionAttachments.restoreInventory(player);

        DragonSpeechNetworking.sendPossessionState(player, -1, false);
        PossessionAbilities.forget(player);
        if (message != null) {
            player.sendSystemMessage(Component.literal(message));
        }
    }

    private static void removeIfStillGranted(ServerPlayer player, EquipmentSlot slot, ItemStack granted) {
        if (granted.isEmpty()) {
            return;
        }
        ItemStack current = player.getItemBySlot(slot);
        if (ItemStack.isSameItemSameComponents(current, granted)) {
            player.setItemSlot(slot, ItemStack.EMPTY);
        }
    }

    private static void dropLeftovers(ServerPlayer player) {
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty()) {
                player.drop(stack.copy(), false);
                inventory.setItem(i, ItemStack.EMPTY);
            }
        }
    }

    /** Saved-inventory NBT round-trip helpers, shared with PossessionAttachments. */
    static ListTag writeInventory(ServerPlayer player) {
        return player.getInventory().save(new ListTag());
    }

    static void readInventory(ServerPlayer player, ListTag tag) {
        player.getInventory().load(tag);
        player.inventoryMenu.broadcastChanges();
    }

    static CompoundTag wrap(ListTag list) {
        CompoundTag tag = new CompoundTag();
        tag.put("inventory", list);
        return tag;
    }
}
