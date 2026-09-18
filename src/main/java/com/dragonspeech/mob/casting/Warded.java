package com.dragonspeech.mob.casting;

import net.minecraft.world.entity.LivingEntity;
import java.util.Map;

/** Any entity carrying active wards - Elder Elf and Shade, currently. Split out from SpellcastingMob since Elf/Human Mage don't get wards. Map (not Set) since each active ward now has its own depleting durability - see MobWards.WardInstance. */
public interface Warded {
    Map<MobWards.WardType, MobWards.WardInstance> activeWards();
    LivingEntity asEntity();
}
