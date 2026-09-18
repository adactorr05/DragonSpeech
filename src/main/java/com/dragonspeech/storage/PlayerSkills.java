package com.dragonspeech.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Skills gating things that are learned rather than simply cast - the
 * two stamina-storage skills, and now the five Phase 6 mind-combat
 * skills. All are unlocked by discovering the specific word that
 * teaches them (see SkillHooks), never granted directly by casting.
 *
 * The mind skills matter more than the storage ones for HOW they gate
 * things: canReachOut is the entire reason "reaching out with your
 * mind" is a trained SKILL and not just another word you can speak -
 * ContactResolver refuses the attempt outright without it, full stop,
 * regardless of how many mind-domain words the player otherwise knows.
 * hugsnert/hugleita/hugvarna/hugrista/hugbinda still exist as words in
 * the dictionary, but the mind-duel abilities they unlock are checked
 * here as booleans, not by re-parsing a cast sentence every time.
 */
public record PlayerSkills(
    boolean senseStamina,
    boolean gatherStamina,
    boolean canSenseMinds,
    boolean canReachOut,
    boolean canWallMind,
    boolean canReadThoughts,
    boolean canBindTotally,
    boolean canEnchant
) {

    public static final Codec<PlayerSkills> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.BOOL.optionalFieldOf("sense_stamina", false).forGetter(PlayerSkills::senseStamina),
        Codec.BOOL.optionalFieldOf("gather_stamina", false).forGetter(PlayerSkills::gatherStamina),
        Codec.BOOL.optionalFieldOf("can_sense_minds", false).forGetter(PlayerSkills::canSenseMinds),
        Codec.BOOL.optionalFieldOf("can_reach_out", false).forGetter(PlayerSkills::canReachOut),
        Codec.BOOL.optionalFieldOf("can_wall_mind", false).forGetter(PlayerSkills::canWallMind),
        Codec.BOOL.optionalFieldOf("can_read_thoughts", false).forGetter(PlayerSkills::canReadThoughts),
        Codec.BOOL.optionalFieldOf("can_bind_totally", false).forGetter(PlayerSkills::canBindTotally),
        Codec.BOOL.optionalFieldOf("can_enchant", false).forGetter(PlayerSkills::canEnchant)
    ).apply(instance, PlayerSkills::new));

    public static PlayerSkills none() {
        return new PlayerSkills(false, false, false, false, false, false, false, false);
    }

    public PlayerSkills withSenseStamina(boolean value) {
        return new PlayerSkills(value, gatherStamina, canSenseMinds, canReachOut, canWallMind, canReadThoughts, canBindTotally, canEnchant);
    }

    public PlayerSkills withGatherStamina(boolean value) {
        return new PlayerSkills(senseStamina, value, canSenseMinds, canReachOut, canWallMind, canReadThoughts, canBindTotally, canEnchant);
    }

    public PlayerSkills withCanSenseMinds(boolean value) {
        return new PlayerSkills(senseStamina, gatherStamina, value, canReachOut, canWallMind, canReadThoughts, canBindTotally, canEnchant);
    }

    public PlayerSkills withCanReachOut(boolean value) {
        return new PlayerSkills(senseStamina, gatherStamina, canSenseMinds, value, canWallMind, canReadThoughts, canBindTotally, canEnchant);
    }

    public PlayerSkills withCanWallMind(boolean value) {
        return new PlayerSkills(senseStamina, gatherStamina, canSenseMinds, canReachOut, value, canReadThoughts, canBindTotally, canEnchant);
    }

    public PlayerSkills withCanReadThoughts(boolean value) {
        return new PlayerSkills(senseStamina, gatherStamina, canSenseMinds, canReachOut, canWallMind, value, canBindTotally, canEnchant);
    }

    public PlayerSkills withCanBindTotally(boolean value) {
        return new PlayerSkills(senseStamina, gatherStamina, canSenseMinds, canReachOut, canWallMind, canReadThoughts, value, canEnchant);
    }

    /** "gala" (general) OR any specific enchantment word - see SkillHooks. Gates ApplyEnchantmentEffectHandler entirely; knowing individual enchant words without this somehow being true isn't a reachable state, but the check stays explicit rather than assumed. */
    public PlayerSkills withCanEnchant(boolean value) {
        return new PlayerSkills(senseStamina, gatherStamina, canSenseMinds, canReachOut, canWallMind, canReadThoughts, canBindTotally, value);
    }
}
