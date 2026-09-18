package com.dragonspeech.race;

import com.dragonspeech.DragonSpeech;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.core.Holder;

/**
 * Applies the two attribute-based traits (Dwarf toughness, Urgal extra
 * health) via a permanent AttributeModifier, reapplied on every join
 * (attribute modifiers do not themselves persist in the attachment
 * system, so this keeps them in sync with whatever race is on file).
 *
 * SCOPED HONESTLY: Elf's "resists mental intrusion" trait is flavor-
 * documented on RaceType but NOT mechanically wired into the mind-duel
 * system yet - that system is large (50+ files) and was built outside
 * this session, and guessing at its internals risked a subtly wrong
 * integration rather than a real one. Wiring it into
 * MindFortitudeService/ResistanceCheck is a clean, contained follow-up
 * once those are reviewed properly.
 */
public final class RaceTraitHooks {

    private static final ResourceLocation KNOCKBACK_MODIFIER_ID = DragonSpeech.id("dwarf_toughness");
    private static final ResourceLocation HEALTH_MODIFIER_ID = DragonSpeech.id("urgal_hardiness");

    private RaceTraitHooks() {}

    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> apply(handler.getPlayer()));
    }

    public static void apply(ServerPlayer player) {
        var race = RaceAccess.get(player).race();

        setModifier(player, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_MODIFIER_ID,
                race.filter(r -> r == RaceType.DWARF).isPresent() ? 0.2 : 0.0);

        setModifier(player, Attributes.MAX_HEALTH, HEALTH_MODIFIER_ID,
                race.filter(r -> r == RaceType.URGAL).isPresent() ? 4.0 : 0.0);
    }

    private static void setModifier(ServerPlayer player, Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
                                    ResourceLocation id, double value) {
        var instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        instance.removeModifier(id);
        if (value != 0.0) {
            instance.addPermanentModifier(new AttributeModifier(id, value, AttributeModifier.Operation.ADD_VALUE));
        }
    }
}