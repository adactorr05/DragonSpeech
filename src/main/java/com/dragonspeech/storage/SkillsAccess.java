package com.dragonspeech.storage;

import net.minecraft.server.level.ServerPlayer;

public final class SkillsAccess {

    private SkillsAccess() {}

    public static PlayerSkills get(ServerPlayer player) {
        PlayerSkills data = player.getAttached(PlayerSkillsAttachments.SKILLS);
        return data != null ? data : PlayerSkills.none();
    }

    public static void set(ServerPlayer player, PlayerSkills skills) {
        player.setAttached(PlayerSkillsAttachments.SKILLS, skills);
    }
}
