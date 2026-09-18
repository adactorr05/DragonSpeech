package com.dragonspeech.eldunari;

import com.dragonspeech.entity.DragonSpeechEntities;
import com.dragonspeech.mind.ContactResolver;
import com.dragonspeech.mind.DuelOutcome;
import com.dragonspeech.mind.EntityLookup;
import com.dragonspeech.mind.MindDuelService;
import com.dragonspeech.storage.DragonSpeechComponents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Renamed from EldunariService - see DragonHeartState's own doc for the
 * rename's reasoning. DragonSpeechComponents' own data component field
 * names (ELDUNARI_STATE, ELDUNARI_ENERGY, ELDUNARI_ID,
 * ELDUNARI_SOURCE_NAME) were deliberately NOT renamed as part of this
 * pass - those are also the literal persistent NBT keys saved into
 * existing worlds; renaming them would orphan any heart items players
 * already have, a real save-compatibility break for zero player-facing
 * benefit (the component NAMES were never shown to players in the first
 * place - only the class names carrying "Eldunari" that were).
 *
 * See DragonHeartVesselEntity's javadoc for why a marker entity is the
 * mechanism at all. This class is the glue: track which vessel belongs
 * to which player's which item, react when MindDuelService decides an
 * outcome, and tidy up if contact never actually connects (defender
 * resisted, on cooldown, etc - see ContactResolver.ContactOutcome).
 */
public final class DragonHeartService {

    private record Pending(UUID playerId, UUID eldunariItemId) {}

    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();
    private static int cleanupCounter = 0;
    /** Passive stored-strength recovery. Tuned per real second (cleanupTick runs once/20 ticks). */
    private static final float HEART_REGEN_PER_SECOND = 2.0f;
    private static final float MAD_HEART_REGEN_PER_SECOND = 0.75f;

    private DragonHeartService() {}

    public static void register() {
        MindDuelService.END_LISTENERS.add(DragonHeartService::onDuelEnded);
        ServerTickEvents.END_SERVER_TICK.register(DragonHeartService::cleanupTick);
    }

    static final float BROKEN_ENERGY = 400f;
    /** "the dragon is MAD... even more difficult to break into, AND do not have nearly as much stamina" per explicit direction. */
    static final float MAD_BROKEN_ENERGY = 150f;

    /**
     * "Gain permission ... or broken its mind" - the BREAK path. Spawns a
     * transient vessel at the player's position, marks the item CONTESTED
     * so a second attempt can't be started on the same stack mid-duel,
     * and kicks off an ordinary ContactResolver attempt against it - from
     * here on this is a completely normal mind duel; DRAGON-tier,
     * MobMindCombatAI-driven defense, the works.
     *
     * Mad hearts (see DragonHeartItem.isMad()) get a HIGHER mind-power
     * multiplier - already harder than every other duel type in the game
     * as a plain DRAGON-tier fight, made harder still on top of that -
     * matching "even more difficult to break into" exactly.
     */
    public static void beginContact(ServerPlayer player, ItemStack heartStack) {
        DragonHeartState state = currentState(heartStack);
        if (state == DragonHeartState.CONTESTED) {
            player.sendSystemMessage(Component.literal("You are already reaching for this heart's mind."));
            return;
        }
        // FIX: "both messages have appeared at the same time" - this
        // check used to look only at the raw stack-level state, which
        // doesn't know about per-player authorization at all (added in
        // an earlier round - see HEART_AUTHORIZED_PLAYER). DragonHeartItem.
        // use() correctly identifies "this is a different person" and
        // calls this method to start their own real duel - but this
        // method then immediately bailed out anyway, because the STACK
        // still said usable() even though that access was earned by
        // someone else. Now only treats it as "already answers to you"
        // if the CURRENT player is the one actually authorized.
        java.util.UUID authorized = heartStack.get(DragonSpeechComponents.HEART_AUTHORIZED_PLAYER);
        if (state.usable() && (authorized == null || authorized.equals(player.getUUID()))) {
            player.sendSystemMessage(Component.literal("This heart already answers to you."));
            return;
        }

        boolean mad = heartStack.getItem() instanceof DragonHeartItem heartItem && heartItem.isMad();

        UUID itemId = ensureId(heartStack);
        var vessel = DragonSpeechEntities.DRAGON_HEART_VESSEL.create(player.serverLevel());
        if (vessel == null) {
            return;
        }
        vessel.moveTo(player.getX() + 0.5, player.getY() + 0.5, player.getZ() + 0.5, 0f, 0f);
        vessel.setMindPowerMultiplier(mad
                ? 0.85f + player.getRandom().nextFloat() * 0.15f  // 0.85-1.0, a genuinely brutal fight on top of DRAGON-tier's own baseline
                : 0.55f + player.getRandom().nextFloat() * 0.2f); // unchanged for real hearts
        player.serverLevel().addFreshEntity(vessel);

        heartStack.set(DragonSpeechComponents.ELDUNARI_STATE, DragonHeartState.CONTESTED.name());
        PENDING.put(vessel.getUUID(), new Pending(player.getUUID(), itemId));

        var outcome = ContactResolver.attempt(player, vessel);
        player.sendSystemMessage(Component.literal(outcome.message()));
        if (!outcome.pending()) {
            // Gate failed outright (no canReachOut skill, on cooldown) - the
            // duel will never start, so there's nothing for the vessel's
            // own CONTACT_WINDOW_TICKS timeout to clean up in time for the
            // player's next attempt. Reset immediately instead of waiting.
            heartStack.set(DragonSpeechComponents.ELDUNARI_STATE, DragonHeartState.UNBONDED.name());
            PENDING.remove(vessel.getUUID());
            vessel.discard();
        }
    }

    private static void onDuelEnded(MinecraftServer server, com.dragonspeech.mind.ActiveMindDuel duel, DuelOutcome outcome) {
        Pending pending = PENDING.remove(duel.defenderId());
        if (pending == null) {
            return; // not a Dragon Heart duel - some other duel entirely
        }

        ServerPlayer player = server.getPlayerList().getPlayer(pending.playerId());
        if (player == null) {
            return; // logged off mid-duel - the item just stays CONTESTED until they next try, which is safe if unusual
        }
        ItemStack stack = findById(player, pending.eldunariItemId());
        if (stack == null) {
            return; // dropped/lost the item mid-duel - nothing to write back to
        }

        if (outcome == DuelOutcome.ATTACKER_VICTORY) {
            stack.set(DragonSpeechComponents.ELDUNARI_STATE, DragonHeartState.BROKEN.name());
            stack.set(DragonSpeechComponents.HEART_AUTHORIZED_PLAYER, player.getUUID());
            Float existingEnergy = stack.get(DragonSpeechComponents.ELDUNARI_ENERGY);
            if (existingEnergy == null || existingEnergy <= 0f) {
                boolean mad = stack.getItem() instanceof DragonHeartItem heartItem && heartItem.isMad();
                // "Found within the world or just gotten in creative...
                // not from your bonded dragon. Their stamina should be
                // randomized from an Adult dragon's stamina to an Elder
                // or Ancient stamina. They should not all be 400
                // stamina" per explicit direction. This is exactly that
                // case - a heart reaching BROKEN with no prior energy
                // set has no living source dragon (one taken via Give
                // Heart already has its real energy set at creation
                // time, so it never falls into this branch at all).
                // Mad hearts deliberately stay at the flat, low
                // MAD_BROKEN_ENERGY - randomizing those too would dilute
                // "mad hearts are weak" as a design point.
                float rolled = mad
                        ? MAD_BROKEN_ENERGY
                        : com.dragonspeech.config.DragonSpeechConfig.foundHeartStaminaMin()
                          + player.getRandom().nextFloat() * (com.dragonspeech.config.DragonSpeechConfig.foundHeartStaminaMax() - com.dragonspeech.config.DragonSpeechConfig.foundHeartStaminaMin());
                stack.set(DragonSpeechComponents.ELDUNARI_ENERGY, rolled);
                stack.set(DragonSpeechComponents.HEART_MAX_ENERGY, rolled);
            }
            player.sendSystemMessage(Component.literal("The heart's resistance gives way. Its stored strength is yours to draw on now."));
            // "When winning a Dragon Heart's Mind, it should unlock the
            // Dragon Heart GUI not the mind connected one" per explicit
            // direction - open it immediately rather than requiring a
            // second right-click.
            openHeartScreen(player, stack);
        } else {
            stack.set(DragonSpeechComponents.ELDUNARI_STATE, DragonHeartState.UNBONDED.name());
            player.sendSystemMessage(Component.literal("The heart's mind holds firm against you. It remains closed."));
        }
    }

    /**
     * "Replaced with a button on the bonded dragon gui that is there
     * when the heart is there and is gone when the heart has been
     * given. When you press the button, you are given that dragon's
     * heart" per explicit direction - this is the entire mechanism now.
     * The old "/dragon eldunari" command (removed - see DragonCommands)
     * was repeatable with no per-dragon limit at all; this is a genuine
     * one-time action, gated on DragonEntity.heartGiven() and setting it
     * true immediately, which (per DragonEntity's own doc) survives
     * death and resurrection automatically since revival reconstructs
     * the dragon from its own saved NBT.
     *
     * Freely given, so PERMITTED (no duel needed) - matches "For your
     * bonded dragon, if given freely it doesn't need a mind duel" per
     * explicit direction. Full, non-mad energy - a bonded owner's own
     * dragon isn't the "crazed" mad variant by definition.
     */
    public static void giveOwnHeart(ServerPlayer player, UUID dragonId) {
        var dragon = com.dragonspeech.dragon.DragonEntity.findNearestBonded(player, 64.0)
                .filter(d -> d.getUUID().equals(dragonId));
        if (dragon.isEmpty()) {
            player.sendSystemMessage(Component.literal("That dragon is not bonded to you, or is not nearby."));
            return;
        }
        if (dragon.get().heartGiven()) {
            player.sendSystemMessage(Component.literal("This dragon's heart has already been given - it will not yield another."));
            return;
        }

        var item = DragonSpeechHearts.byColor(dragon.get().color());
        if (item == null) {
            return;
        }
        // "Should contain the stamina that the dragons age was when it
        // was removed" per explicit direction - snapshotted once, right
        // now, from the dragon's own real maxDragonStamina() (which
        // already scales with age - a hatchling's real cap is much
        // lower than an elder's). Locked into HEART_MAX_ENERGY
        // permanently from this point on, even if this same dragon (if
        // it's still alive) keeps aging and growing past this value
        // later - the heart doesn't track that, only its own snapshot.
        float ageLockedMax = dragon.get().maxDragonStamina();

        ItemStack heart = new ItemStack(item);
        heart.set(DragonSpeechComponents.ELDUNARI_STATE, DragonHeartState.PERMITTED.name());
        heart.set(DragonSpeechComponents.ELDUNARI_ENERGY, ageLockedMax);
        heart.set(DragonSpeechComponents.HEART_MAX_ENERGY, ageLockedMax);
        heart.set(DragonSpeechComponents.ELDUNARI_ID, UUID.randomUUID());
        heart.set(DragonSpeechComponents.ELDUNARI_SOURCE_NAME, dragon.get().color().getSerializedName() + " dragon");
        heart.set(DragonSpeechComponents.HEART_AUTHORIZED_PLAYER, player.getUUID());

        if (!player.getInventory().add(heart)) {
            player.drop(heart, false);
        }
        dragon.get().setHeartGiven(true);
        player.sendSystemMessage(Component.literal("Your dragon offers its heart to you freely - it answers to you without contest."));
    }

    /** Shared by both grant paths (duel victory, freely given) - opens the real Dragon Heart screen with a full state snapshot, same payload DragonHeartItem.use() sends for an already-usable heart. */
    static void openHeartScreen(ServerPlayer player, ItemStack stack) {
        String colorName = stack.getItem() instanceof DragonHeartItem heartItem
                ? (heartItem.isMad() ? "mad" : heartItem.color().getSerializedName())
                : "mad";
        Float energyBoxed = stack.get(DragonSpeechComponents.ELDUNARI_ENERGY);
        float energy = energyBoxed != null ? energyBoxed : 0f;
        float maxEnergy = resolveMaxEnergy(stack);
        Boolean useStaminaBoxed = stack.get(DragonSpeechComponents.HEART_USE_STAMINA);
        boolean useStamina = useStaminaBoxed == null || useStaminaBoxed;
        Boolean beforeOwnBoxed = stack.get(DragonSpeechComponents.HEART_STAMINA_BEFORE_OWN);
        boolean beforeOwn = beforeOwnBoxed == null || beforeOwnBoxed;
        UUID heartId = stack.get(DragonSpeechComponents.ELDUNARI_ID);
        if (heartId == null) {
            heartId = UUID.randomUUID();
            stack.set(DragonSpeechComponents.ELDUNARI_ID, heartId);
        }
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
                new com.dragonspeech.network.OpenDragonHeartScreenPayload(heartId, colorName, energy, maxEnergy, useStamina, beforeOwn));
    }

    /**
     * "Contain the stamina that the dragons age was when it was
     * removed" per explicit direction - a real, age-locked per-item cap
     * (see HEART_MAX_ENERGY's own doc) if this heart came from a living
     * dragon via giveOwnHeart. Falls back to the flat MAD_BROKEN_ENERGY/
     * BROKEN_ENERGY constants for a found/combat heart with no source
     * dragon to have ever locked a real value in.
     */
    static float resolveMaxEnergy(ItemStack stack) {
        Float stored = stack.get(DragonSpeechComponents.HEART_MAX_ENERGY);
        if (stored != null) {
            return stored;
        }
        boolean mad = stack.getItem() instanceof DragonHeartItem heartItem && heartItem.isMad();
        return mad ? MAD_BROKEN_ENERGY : BROKEN_ENERGY;
    }

    public static ItemStack findById(ServerPlayer player, UUID id) {
        for (ItemStack stack : player.getInventory().items) {
            if (id.equals(stack.get(DragonSpeechComponents.ELDUNARI_ID))) {
                return stack;
            }
        }
        return null;
    }

    private static void cleanupTick(MinecraftServer server) {
        cleanupCounter++;
        if (cleanupCounter < 20) {
            return;
        }
        cleanupCounter = 0;
        regenerateHeldHearts(server);
        PENDING.entrySet().removeIf(entry -> {
            if (EntityLookup.byUUID(server, entry.getKey()) != null) {
                return false; // vessel still exists - contact still pending or duel still running, leave it
            }
            // Vessel is gone but PENDING still has an entry for it - the
            // only way that happens is CONTACT_WINDOW_TICKS timing out
            // with contact never succeeding (onDuelEnded already removes
            // the entry itself on a real outcome). Reset the stuck item.
            Pending pending = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(pending.playerId());
            if (player != null) {
                ItemStack stack = findById(player, pending.eldunariItemId());
                if (stack != null && currentState(stack) == DragonHeartState.CONTESTED) {
                    stack.set(DragonSpeechComponents.ELDUNARI_STATE, DragonHeartState.UNBONDED.name());
                }
            }
            return true;
        });
    }

    /**
     * Dragon Hearts are living reservoirs, not one-shot batteries. Once a heart is usable,
     * its stored strength naturally recovers toward its age-locked cap even while idle.
     * Regeneration belongs to the heart item itself; authorization controls who may DRAW
     * from it, not whether the contained dragon-mind can recover.
     */
    private static void regenerateHeldHearts(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            for (ItemStack stack : player.getInventory().items) regenerateHeartStack(stack);
            for (ItemStack stack : player.getInventory().offhand) regenerateHeartStack(stack);
        }
    }

    private static void regenerateHeartStack(ItemStack stack) {
        if (!(stack.getItem() instanceof DragonHeartItem heart)) return;
        DragonHeartState state = currentState(stack);
        if (!state.usable()) return;
        float max = resolveMaxEnergy(stack);
        Float raw = stack.get(DragonSpeechComponents.ELDUNARI_ENERGY);
        float current = raw == null ? 0f : raw;
        if (current >= max) return;
        float regen = heart.isMad() ? MAD_HEART_REGEN_PER_SECOND : HEART_REGEN_PER_SECOND;
        stack.set(DragonSpeechComponents.ELDUNARI_ENERGY, Math.min(max, current + regen));
    }

    static DragonHeartState currentState(ItemStack stack) {
        String raw = stack.get(DragonSpeechComponents.ELDUNARI_STATE);
        if (raw == null) {
            return DragonHeartState.UNBONDED;
        }
        try {
            return DragonHeartState.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return DragonHeartState.UNBONDED;
        }
    }

    private static UUID ensureId(ItemStack stack) {
        UUID id = stack.get(DragonSpeechComponents.ELDUNARI_ID);
        if (id == null) {
            id = UUID.randomUUID();
            stack.set(DragonSpeechComponents.ELDUNARI_ID, id);
        }
        return id;
    }
}