package com.dragonspeech.dragon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;

import java.util.Optional;
import java.util.UUID;

/**
 * "There should only be 1 bonded dragon each player alive at a time.
 * The config option gives it another piece where you can stop players
 * from bonding to another if their previous bonded dragon dies" per
 * explicit direction.
 *
 * currentBondedDragon is the ALWAYS-enforced half of that rule (never
 * configurable) - if present AND that dragon is still actually alive,
 * no new bond can be attempted at all, full stop.
 *
 * hasHadBondedDragonDie is the config-gated half - once true, whether a
 * NEW bond is still allowed depends entirely on
 * DragonSpeechConfig.allowRebondAfterDeath().
 */
public record PlayerBondData(Optional<UUID> currentBondedDragon, boolean hasHadBondedDragonDie) {

    public static final Codec<PlayerBondData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        UUIDUtil.CODEC.optionalFieldOf("current_bonded_dragon").forGetter(PlayerBondData::currentBondedDragon),
        Codec.BOOL.fieldOf("has_had_bonded_dragon_die").forGetter(PlayerBondData::hasHadBondedDragonDie)
    ).apply(instance, PlayerBondData::new));

    public static PlayerBondData initial() {
        return new PlayerBondData(Optional.empty(), false);
    }

    public PlayerBondData withCurrentBondedDragon(UUID dragonId) {
        return new PlayerBondData(Optional.of(dragonId), hasHadBondedDragonDie);
    }

    /** Called when a bonded dragon dies - clears the "currently bonded" half and permanently marks "has had one die," regardless of what happens after. */
    public PlayerBondData afterBondedDragonDied() {
        return new PlayerBondData(Optional.empty(), true);
    }
}
