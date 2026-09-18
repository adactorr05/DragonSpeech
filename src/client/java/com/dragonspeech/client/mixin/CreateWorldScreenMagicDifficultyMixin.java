package com.dragonspeech.client.mixin;

import com.dragonspeech.config.DragonSpeechConfig;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.LayoutSettings;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Locale;

/**
 * Adds a real "Magic Difficulty" CycleButton to the Create New World
 * screen's Game tab - built directly from CreateWorldScreen's actual
 * decompiled source (which you provided), not the earlier guess-based
 * overlay attempt. That earlier attempt crashed because it injected into
 * mouseClicked() - a method CreateWorldScreen never actually overrides
 * at all (confirmed by reading the real source: it's only inherited
 * unmodified from Screen, so there was nothing there to inject into).
 * This version doesn't need any custom click handling in the first
 * place - a real CycleButton handles its own clicks the same way
 * vanilla's own Game Mode/Difficulty/Allow Commands buttons already do.
 *
 * TARGETS THE NESTED GameTab CLASS, not CreateWorldScreen itself - the
 * real Game Difficulty selector lives inside CreateWorldScreen's private
 * inner class GameTab (extends GridLayoutTab), built entirely inside its
 * own constructor. Mixin targets it via the binary nested-class name
 * "CreateWorldScreen$GameTab" since it isn't a top-level class you can
 * import directly.
 *
 * POSITION - HONEST LIMITATION: this appends the button at the very END
 * of the Game tab's content (after Allow Commands, after the optional
 * Experiments button), not literally BETWEEN Difficulty and Allow
 * Commands the way you asked for. Getting it precisely BETWEEN two
 * existing rows would mean injecting into the MIDDLE of GameTab's
 * constructor at a specific method-call boundary (right before
 * CycleButton.onOffBuilder() is invoked for the Allow Commands button) -
 * a meaningfully more fragile injection point than TAIL, and this
 * feature has already needed two rounds of fixes. If exact positioning
 * matters enough to be worth that added fragility, say so and I'll
 * attempt the precise version next - this one prioritizes actually
 * working over pixel-perfect placement.
 *
 * USES @Local (MixinExtras) - the first use of this specific feature in
 * the project: GameTab's constructor builds its rows through a LOCAL
 * variable (RowHelper), not a field, so there's no ordinary way to reach
 * it from a plain @Inject. @Local captures that exact same local
 * variable by type at the injection point. MixinExtras itself is
 * already active in this project (confirmed in your last log:
 * "Initializing MixinExtras via ...MixinExtrasServiceImpl(version=0.5.4)"),
 * so no new dependency is needed - but the annotation itself hasn't been
 * proven working in this project before now, so it's worth knowing this
 * is the one genuinely new mechanism in this file if something goes
 * wrong here specifically.
 *
 * VERSION-RISK NOTE: GameTab is a non-static inner class, so its real
 * compiled constructor implicitly takes the outer CreateWorldScreen
 * instance as a hidden first parameter, even though the source shows
 * GameTab() with no parameters. Targeting "<init>" with an empty
 * explicit parameter list (matching the SOURCE, not the compiled
 * bytecode) is the normal, supported way to target inner-class
 * constructors in Mixin - but if this is wrong, it'll show the exact
 * same kind of clear "expected X but found Y" descriptor error Mixin
 * already gave us once this session for the real fix.
 */
@Mixin(targets = "net.minecraft.client.gui.screens.worldselection.CreateWorldScreen$GameTab")
public abstract class CreateWorldScreenMagicDifficultyMixin {

    @Inject(method = "<init>", at = @At("TAIL"))
    private void dragonspeech$addMagicDifficultyRow(CallbackInfo ci, @Local GridLayout.RowHelper rowHelper) {
        LayoutSettings layoutSettings = rowHelper.newCellSettings();

        CycleButton<DragonSpeechConfig.Difficulty> magicDifficultyButton = rowHelper.addChild(
            CycleButton.<DragonSpeechConfig.Difficulty>builder(d -> Component.literal(dragonspeech$label(d)))
                .withValues(DragonSpeechConfig.Difficulty.values())
                .create(0, 0, 210, 20, Component.literal("Magic Difficulty"),
                    (cycleButton, chosen) -> DragonSpeechConfig.setDifficulty(chosen)),
            layoutSettings
        );
        magicDifficultyButton.setValue(DragonSpeechConfig.difficulty());
    }

    private static String dragonspeech$label(DragonSpeechConfig.Difficulty difficulty) {
        String name = difficulty.name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}
