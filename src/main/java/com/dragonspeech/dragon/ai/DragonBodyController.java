package com.dragonspeech.dragon.ai;

import com.dragonspeech.dragon.DragonEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.control.BodyRotationControl;

/**
 * Ported near-verbatim from Dragon Mounts Legacy
 * (com.github.kay9.dragonmounts.dragon.ai.DragonBodyController,
 * GPL-3.0). Keeps the body's yaw locked to the entity's actual facing
 * (no independent body-vs-look wobble a long-necked creature doesn't
 * need) and clamps head rotation so the neck can't visually twist past
 * its own max turn angle.
 */
public class DragonBodyController extends BodyRotationControl {
    private final DragonEntity dragon;

    public DragonBodyController(DragonEntity dragon) {
        super(dragon);
        this.dragon = dragon;
    }

    @Override
    public void clientTick() {
        dragon.yBodyRot = dragon.getYRot();

        dragon.yHeadRot = Mth.rotateIfNecessary(dragon.yHeadRot, dragon.yBodyRot, dragon.getMaxHeadYRot());
    }
}
