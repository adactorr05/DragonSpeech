package com.dragonspeech.mind;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.UUID;

/**
 * A mind-duel participant is very often a mob, not a player, so "find the
 * entity for this UUID" needs to check every loaded level, not just
 * PlayerList (which only knows about players). Used wherever a
 * CommandEffect needs to actually touch the defender's entity.
 */
public final class EntityLookup {

    private EntityLookup() {}

    public static Entity byUUID(MinecraftServer server, UUID id) {
        Entity player = server.getPlayerList().getPlayer(id);
        if (player != null) {
            return player;
        }
        for (ServerLevel level : server.getAllLevels()) {
            Entity found = level.getEntity(id);
            if (found != null) {
                return found;
            }
        }
        return null;
    }
}
