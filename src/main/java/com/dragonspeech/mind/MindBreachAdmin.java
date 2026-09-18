package com.dragonspeech.mind;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Backs "/dragonspeech forcebreach <true|false>" - admin-only, default
 * false.
 *
 * REDESIGNED AGAIN per explicit direction: "I wish for this not to be
 * a right click action. It should happen when I use my mind skill. Not
 * a right click. When it is a right click, it interrupts the right
 * click of the dragon heart as well... I want it to take over the Mind
 * Duel when the command is enabled." The previous version hooked
 * UseEntityCallback directly - a completely separate right-click
 * interaction that competed with (and sometimes won over) whatever else
 * a right-click was supposed to do, including opening the Dragon Heart
 * screen. This almost certainly explains "it still goes to the Occupied
 * Mind/mind connected screen instead of the Dragon Heart Screen" too -
 * a stray, unrelated right-click hook firing near a freshly-spawned
 * vessel entity (which spawns essentially on top of the player - see
 * DragonHeartService.beginContact) could start a second, redundant
 * forced duel against it, independent of the real one
 * DragonHeartService itself already started properly.
 *
 * Now: no interaction hook of any kind. Registers a MindDuelManager.
 * START_LISTENERS callback instead - the one universal point every
 * duel actually starts through, regardless of source (a player's own
 * mind skill, DragonHeartService.beginContact, anything else). When an
 * admin with forcebreach enabled becomes the ATTACKER of any real duel,
 * that duel's defender barrier is immediately dropped to 4% the moment
 * it starts - "take over the Mind Duel" exactly as described, not a
 * separate trigger competing with it.
 */
public final class MindBreachAdmin {

    private MindBreachAdmin() {}

    /** Starting integrity is 100 - dropping to exactly 4 needs damaging by 96. */
    private static final float DAMAGE_TO_REACH_4_PERCENT = 96f;

    private static final Set<UUID> ENABLED = new HashSet<>();

    /** Cached at server start purely so the START_LISTENERS callback below (which only receives the ActiveMindDuel itself, not a server reference) can still look up both participants to send confirmation messages - same low-risk pattern as StructurePoolDiagnostic's own startup hook, rather than changing MindDuelManager.start()'s signature for every existing caller just for this. */
    private static net.minecraft.server.MinecraftServer cachedServer;

    public static void set(ServerPlayer player, boolean enabled) {
        if (enabled) {
            ENABLED.add(player.getUUID());
        } else {
            ENABLED.remove(player.getUUID());
        }
    }

    public static void register() {
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(server -> cachedServer = server);

        MindDuelManager.START_LISTENERS.add(duel -> {
            if (!ENABLED.contains(duel.attackerId())) {
                return;
            }
            duel.damageDefenderBarrier(DAMAGE_TO_REACH_4_PERCENT);

            if (cachedServer == null) {
                return;
            }
            ServerPlayer admin = cachedServer.getPlayerList().getPlayer(duel.attackerId());
            if (admin != null) {
                admin.sendSystemMessage(Component.literal("[Admin] Their mind's defenses are crippled to 4% - one real attack will finish it."));
            }
            if (EntityLookup.byUUID(cachedServer, duel.defenderId()) instanceof ServerPlayer defenderPlayer) {
                defenderPlayer.sendSystemMessage(Component.literal("Something presses against your mind, and your defenses buckle."));
            }
        });
    }
}